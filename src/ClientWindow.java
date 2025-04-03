import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;
//import java.util.Timer;
//import java.util.TimerTask;
import javax.swing.*;

public class ClientWindow implements ActionListener {
    private int userScore = 0;
    private JButton poll, submit;
    private JRadioButton[] options;
    private ButtonGroup optionGroup;
    private JLabel question, timer, score, gameMessage;
    //private TimerTask clock;
    private JFrame window;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String selectedAnswer = "";

    public ClientWindow(String serverIP, int port) {
        window = new JFrame("Trivia Game");
        window.setSize(400, 400);
        window.setLayout(null);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);

        question = new JLabel("Waiting for question...");
        question.setBounds(10, 5, 350, 100);
        window.add(question);

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

        score = new JLabel("Score: " + userScore);
        score.setBounds(50, 250, 100, 20);
        window.add(score);

        timer = new JLabel("Timer");
        timer.setBounds(250, 250, 100, 20);
        window.add(timer);

        gameMessage = new JLabel("");
        gameMessage.setBounds(10, 220, 350, 20);
        window.add(gameMessage);

        poll = new JButton("Poll");
        poll.setBounds(10, 300, 100, 20);
        poll.addActionListener(this);
        poll.setEnabled(false);
        window.add(poll);

        submit = new JButton("Submit");
        submit.setBounds(200, 300, 100, 20);
        submit.addActionListener(this);
        submit.setEnabled(false);
        window.add(submit);

        window.setVisible(true);

        try {
            socket = new Socket(serverIP, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            new Thread(this::listenToServer).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Could not connect to server.");
        }
    }

    private void listenToServer() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("Question")) {
                    StringBuilder fullQuestion = new StringBuilder(line).append("\n");
                    for (int i = 0; i < 5; i++) {
                        String nextLine = in.readLine();
                        if (nextLine != null) fullQuestion.append(nextLine).append("\n");
                    }
                    displayQuestion(fullQuestion.toString());
                } else if (line.startsWith("Your Answer")) {
                    SwingUtilities.invokeLater(() -> poll.setEnabled(true));
                } 
                else if (line.startsWith("ACK")) {
                    SwingUtilities.invokeLater(() -> {
                        gameMessage.setText("You won the buzz! You may answer.");
                        poll.setEnabled(false);
                        submit.setEnabled(true);
                        for (JRadioButton option : options) option.setEnabled(true);
                    });
                } 
                else if (line.startsWith("NAK")) {
                    SwingUtilities.invokeLater(() -> {
                        gameMessage.setText("Too late! Another player buzzed first.");
                        poll.setEnabled(false);
                        submit.setEnabled(false);
                        for (JRadioButton option : options) option.setEnabled(false);
                    });
                }
                
//                else if (line.startsWith("Time")) {
//                    String[] parts = line.split(" ");
//                    if (parts.length == 2) {
//                        try {
//                            int duration = Integer.parseInt(parts[1]);
//                            restartTimer(duration);
//                        } catch (NumberFormatException ignored) {}
//                    }
//                } 
                
                else if (line.startsWith("Timer")) {
                    String[] parts = line.split(" ");
                    if (parts.length == 2) {
                        try {
                            int time = Integer.parseInt(parts[1]);
                            SwingUtilities.invokeLater(() -> {
                                timer.setForeground(time < 6 ? Color.RED : Color.BLACK);
                                timer.setText("Time: " + time);
                            });
                        } catch (NumberFormatException ignored) {}
                    }
                }

                
                else if (line.toLowerCase().startsWith("correct")) {
                    userScore += 10;
                    updateGameMessage("Correct answer! +10 points", Color.GREEN);
                } else if (line.toLowerCase().startsWith("wrong")) {
                    userScore -= 10;
                    updateGameMessage("Wrong answer! -10 points", Color.RED);
                } else if (line.contains("WaitForNextRound")) {
                    updateGameMessage("You joined mid-game. Wait for the next question.", Color.BLUE);
                    disableControls();
                } else if (line.toLowerCase().startsWith("game over")) {
                    JOptionPane.showMessageDialog(null, line);
                    disableControls();
                }
            }
        } catch (IOException e) {
            System.out.println("Disconnected from server.");
        }
    }

    private void updateGameMessage(String msg, Color color) {
        SwingUtilities.invokeLater(() -> {
            gameMessage.setForeground(color);
            gameMessage.setText(msg);
            score.setText("Score: " + userScore);
        });
    }

    private void disableControls() {
        SwingUtilities.invokeLater(() -> {
            poll.setEnabled(false);
            submit.setEnabled(false);
            for (JRadioButton option : options) option.setEnabled(false);
        });
    }

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

    private void sendUDPBuzz() {
        try {
            // To uniquely identify each client, send local port
            String buzzMsg = "buzz:" + socket.getLocalPort();
            byte[] buffer = buzzMsg.getBytes();
            DatagramSocket udpSocket = new DatagramSocket();
            InetAddress serverAddress = socket.getInetAddress();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, 1235);
            udpSocket.send(packet);
            udpSocket.close();
        } catch (IOException e) {
            System.out.println("UDP Buzz failed");
        }
    }

//    private void restartTimer(int duration) {
//        if (clock != null) clock.cancel();
//        clock = new TimerCode(duration);
//        new Timer().schedule(clock, 0, 1000);
//    }

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
            poll.setEnabled(false);
        }

        if (src == submit) {
            if (!selectedAnswer.isEmpty()) {
                out.println(selectedAnswer);
                submit.setEnabled(false);
                for (JRadioButton option : options) option.setEnabled(false);
            } else {
                JOptionPane.showMessageDialog(null, "Please select an answer.");
            }
        }
    }

//    public class TimerCode extends TimerTask {
//        private int duration;
//
//        public TimerCode(int duration) {
//            this.duration = duration;
//        }
//
//        @Override
//        public void run() {
//            if (duration < 0) {
//                timer.setText("Timer expired");
//                out.println("Expired");
//                submit.setEnabled(false);
//                for (JRadioButton option : options) option.setEnabled(false);
//                this.cancel();
//                return;
//            }
//            timer.setForeground(duration < 6 ? Color.RED : Color.BLACK);
//            timer.setText(String.valueOf(duration));
//            duration--;
//        }
//    }
}
