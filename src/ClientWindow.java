import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import javax.swing.*;

public class ClientWindow implements ActionListener {
    private int userScore = 0;
    private JButton poll, submit;
    private JRadioButton[] options;
    private ButtonGroup optionGroup;
    private JLabel question, timer, score, gameMessage;
    private JFrame window;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String selectedAnswer = "";

    // Constructor that initializes the GUI and connects to the server
    public ClientWindow(String serverIP, int port) {
        window = new JFrame("Trivia Game");
        window.setSize(400, 400);
        window.setLayout(null);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);

        // Question label
        question = new JLabel("Waiting for question...");
        question.setBounds(10, 5, 350, 100);
        window.add(question);

        // Multiple-choice options
        options = new JRadioButton[4];
        optionGroup = new ButtonGroup();
        for (int i = 0; i < 4; i++) {
            options[i] = new JRadioButton("Option " + (i + 1));
            options[i].setBounds(10, 110 + i * 20, 350, 20);
            options[i].addActionListener(this);
            options[i].setEnabled(false);
            optionGroup.add(options[i]);
            window.add(options[i]);
        }

        // Score label
        score = new JLabel("Score: " + userScore);
        score.setBounds(50, 250, 100, 20);
        window.add(score);

        // Timer label
        timer = new JLabel("Timer");
        timer.setBounds(250, 250, 100, 20);
        window.add(timer);

        // Message label to display game status
        gameMessage = new JLabel("");
        gameMessage.setBounds(10, 220, 350, 20);
        window.add(gameMessage);

        // Poll button to buzz in
        poll = new JButton("Poll");
        poll.setBounds(10, 300, 100, 20);
        poll.addActionListener(this);
        poll.setEnabled(false);
        window.add(poll);

        // Submit button to send selected answer
        submit = new JButton("Submit");
        submit.setBounds(200, 300, 100, 20);
        submit.addActionListener(this);
        submit.setEnabled(false);
        window.add(submit);

        window.setVisible(true);

        // Connect to server and start listener thread
        try {
            socket = new Socket(serverIP, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            new Thread(this::listenToServer).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Could not connect to server.");
        }
    }
    private String getClientIDFromTitle() {
        String title = window.getTitle();
        int idx = title.lastIndexOf("Client-");
        return idx != -1 ? title.substring(idx) : "";
    }

    // Listens to server messages and updates GUI accordingly
    private void listenToServer() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("Welcome Client-")) {
                    updateGameMessage(line, Color.BLUE);
                    final String titleText = line.replace("Welcome ", "");
                    SwingUtilities.invokeLater(() -> window.setTitle("Trivia Server: " + titleText));
                } else if (line.startsWith("Question")) {
                    // Read and display full question with options
                    StringBuilder fullQuestion = new StringBuilder(line).append("\n");
                    for (int i = 0; i < 5; i++) {
                        String nextLine = in.readLine();
                        if (nextLine != null) fullQuestion.append(nextLine).append("\n");
                    }
                    displayQuestion(fullQuestion.toString());
                } else if (line.startsWith("Your Answer")) {
                    SwingUtilities.invokeLater(() -> poll.setEnabled(true));
                } else if (line.startsWith("ACK")) {
                    // Client won the buzz
                    SwingUtilities.invokeLater(() -> {
                        gameMessage.setText("You won the buzz! You may answer.");
                        poll.setEnabled(false);
                        submit.setEnabled(true);
                        for (JRadioButton option : options) option.setEnabled(true);
                    });
                } else if (line.startsWith("NAK")) {
                    // Another client buzzed first
                    SwingUtilities.invokeLater(() -> {
                        gameMessage.setText("Too late! Another player buzzed first.");
                        poll.setEnabled(false);
                        submit.setEnabled(false);
                        for (JRadioButton option : options) option.setEnabled(false);
                    });
                } else if (line.startsWith("TIMER:")) {
                    // Update timer label
                    String time = line.split(":")[1];
                    SwingUtilities.invokeLater(() -> {
                        int seconds = Integer.parseInt(time);
                        timer.setForeground(seconds < 6 ? Color.RED : Color.BLACK);
                        timer.setText("Time: " + seconds);
                    });
                } else if (line.equals("UNLOCK_POLL")) {
                    SwingUtilities.invokeLater(() -> poll.setEnabled(true));
                } else if (line.toLowerCase().startsWith("correct")) {
                    userScore += 10;
                    updateGameMessage("Correct answer! +10 points", Color.GREEN);
                } else if (line.toLowerCase().startsWith("wrong")) {
                    userScore -= 10;
                    updateGameMessage("Wrong answer! -10 points", Color.RED);
                } else if (line.toLowerCase().startsWith("noanswerpenalty")) {
                    userScore -= 20;
                    updateGameMessage("You did not answer in time. -20 points", Color.RED);
                } else if (line.contains("WaitForNextRound")) {
                    updateGameMessage("You joined mid-game. Wait for the next question.", Color.BLUE);
                    disableControls();
                } else if (line.startsWith("FINAL_SCORE:")) {
                    try {
                        userScore = Integer.parseInt(line.split(":")[1].trim());
                        SwingUtilities.invokeLater(() -> {
                            score.setText("Score: " + userScore);
                            disableControls();
                        });
                    } catch (NumberFormatException ex) {
                        System.out.println("Failed to parse FINAL_SCORE line: " + line);
                    }
                } else if (line.equalsIgnoreCase("KILLSWITCH")) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null, "You have been removed from the game.");
                        System.exit(0);
                    });
                } else if (line.equals("not_enough_players")) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null,
                            "Not enough players joined.\nThe game cannot start.",
                            "Game Cancelled",
                            JOptionPane.WARNING_MESSAGE);
                        System.exit(0);
                    });
                } else if (line.startsWith("SCOREBOARD|")) {
                    String rawData = line.substring("SCOREBOARD|".length());

                    // Split into entries like Client-0:30;Client-1:10
                    String[] entries = rawData.split(";");
                    Map<String, Integer> scores = new HashMap<>();

                    for (String entry : entries) {
                        if (!entry.isEmpty()) {
                            String[] parts = entry.split(":");
                            scores.put(parts[0], Integer.parseInt(parts[1]));
                        }
                    }

                    // Sort by score descending
                    List<Map.Entry<String, Integer>> sorted = new ArrayList<>(scores.entrySet());
                    sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

                    // Build message
                    StringBuilder msg = new StringBuilder();
                    msg.append("Final Scoreboard:\n\n");

                    boolean youWon = false;
                    String yourLabel = "Client " + getClientIDFromTitle(); // helper function

                    for (int i = 0; i < sorted.size(); i++) {
                        Map.Entry<String, Integer> entry = sorted.get(i);
                        msg.append(String.format("%2d. %s — %d pts\n", i + 1, entry.getKey(), entry.getValue()));

                        if (i == 0 && entry.getKey().equals(yourLabel)) {
                            youWon = true;
                        }
                    }

                    msg.append("\n");
                    msg.append(youWon ? "You won!" : "You lost. Better luck next time!");

                    JOptionPane.showMessageDialog(window, msg.toString(), "Game Over", JOptionPane.INFORMATION_MESSAGE);
                }

            }
        } catch (IOException e) {
            System.out.println("Disconnected from server.");
        }
    }

    // Updates game message label with color and message
    private void updateGameMessage(String msg, Color color) {
        SwingUtilities.invokeLater(() -> {
            gameMessage.setForeground(color);
            gameMessage.setText(msg);
            score.setText("Score: " + userScore);
        });
    }

    // Disables all interactive buttons and radio options
    private void disableControls() {
        SwingUtilities.invokeLater(() -> {
            poll.setEnabled(false);
            submit.setEnabled(false);
            for (JRadioButton option : options) option.setEnabled(false);
        });
    }

    // Displays a new question and resets UI components
    private void displayQuestion(String fullText) {
        SwingUtilities.invokeLater(() -> {
            String[] lines = fullText.split("\n");
            if (lines.length < 6) return;
            question.setText("<html><b>" + lines[0] + "</b><br>" + lines[1] + "</html>");
            for (int i = 0; i < 4; i++) {
                options[i].setText(lines[i + 2]);
                options[i].setEnabled(false);
                options[i].setSelected(false);
            }
            optionGroup.clearSelection();
            selectedAnswer = "";
            poll.setEnabled(true);
            submit.setEnabled(false);
        });
    }

    // Sends a UDP buzz packet to the server
    private void sendUDPBuzz() {
        try {
            byte[] buffer = "buzz".getBytes();
            DatagramSocket udpSocket = new DatagramSocket();
            InetAddress serverAddress = socket.getInetAddress();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, 1235);
            udpSocket.send(packet);
            udpSocket.close();
        } catch (IOException e) {
            System.out.println("UDP Buzz failed");
        }
    }

    // Handles user actions from GUI components
    @Override
    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();

        if (src instanceof JRadioButton) {
            for (int i = 0; i < options.length; i++) {
                if (options[i].isSelected()) {
                    selectedAnswer = String.valueOf((char) ('A' + i));
                    break;
                }
            }
        }

        if (src == poll) {
            sendUDPBuzz();
            poll.setEnabled(true);
        }

        if (src == submit) {
            if (!selectedAnswer.isEmpty()) {
                out.println(selectedAnswer);
                submit.setEnabled(false);
                for (JRadioButton option : options) option.setEnabled(false);
            } else {
                gameMessage.setForeground(Color.RED);
                gameMessage.setText("Please select an answer.");
            }
        }
    }
}

