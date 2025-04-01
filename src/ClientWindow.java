import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.TimerTask;
import java.util.Timer;
import javax.swing.*;

public class ClientWindow implements ActionListener {
	private int userScore = 0;
    private JButton poll;
    private JButton submit;
    private JRadioButton[] options;
    private ButtonGroup optionGroup;
    private JLabel question;
    private JLabel timer;
    private JLabel score;
    private JLabel gameMessage;
    private TimerTask clock;
    private JFrame window;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String selectedAnswer = "";

    public ClientWindow(String serverIP, int port) {
        window = new JFrame("Trivia Game");
        question = new JLabel("Waiting for question...");
        window.add(question);
        question.setBounds(10, 5, 350, 100);

        options = new JRadioButton[4];
        optionGroup = new ButtonGroup();
        for (int i = 0; i < 4; i++) {
            options[i] = new JRadioButton("Option " + (i + 1));
            options[i].addActionListener(this);
            options[i].setBounds(10, 110 + (i * 20), 350, 20);
            options[i].setEnabled(false);
            window.add(options[i]);
            optionGroup.add(options[i]);
        }

        timer = new JLabel("TIMER");
        timer.setBounds(250, 250, 100, 20);
        window.add(timer);

        score = new JLabel("Score: " + userScore);
        score.setBounds(50, 250, 100, 20);
        window.add(score);
        
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

        window.setSize(400, 400);
        window.setLayout(null);
        window.setVisible(true);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);

        try {
            socket = new Socket(serverIP, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            new Thread(this::listenToServer).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Could not connect to server.");
            e.printStackTrace();
        }
    }

    private void listenToServer() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("Question")) {
                    StringBuilder fullQuestion = new StringBuilder();
                    fullQuestion.append(line).append("\n");
                    for (int i = 0; i < 5; i++) {
                        String nextLine = in.readLine();
                        if (nextLine == null) break;
                        fullQuestion.append(nextLine).append("\n");
                    }
                    displayQuestion(fullQuestion.toString());
                } else if (line.startsWith("Your Answer")) {
                    poll.setEnabled(true); // Poll opens
                } else if (line.startsWith("ACK")) {
                    SwingUtilities.invokeLater(() -> {
                        gameMessage.setText("You won the buzz! You may answer.");
                        poll.setEnabled(false);
                        submit.setEnabled(true);
                        for (JRadioButton option : options) {
                            option.setEnabled(true);
                        }
                    });
                } else if (line.startsWith("NAK")) {
                    SwingUtilities.invokeLater(() -> {
                        gameMessage.setText("Too late! Another player buzzed first.");
                        poll.setEnabled(false);
                        submit.setEnabled(false);
                        for (JRadioButton option : options) {
                            option.setEnabled(false);
                        }
                    });
                } else if (line.startsWith("Time")) {
                    String[] parts = line.split(" ");
                    if (parts.length == 2) {
                        try {
                            int duration = Integer.parseInt(parts[1]);
                            restartTimer(duration);
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid time format from server: " + line);
                        }
                    }
                } else if(line.toLowerCase().startsWith("correct")) {
                	gameMessage.setText("Correct answer! +10 points");
                	userScore += 10;
                	SwingUtilities.invokeLater(() -> {
                        score.setText("Score: " + userScore);
                    });
                } else if(line.toLowerCase().startsWith("wrong")) {
                	gameMessage.setText("Wrong answer! -10 points");
                	userScore -= 10;
                	SwingUtilities.invokeLater(() -> {
                        score.setText("Score: " + userScore);
                    });
                } else if (line.startsWith("Game Over")) {
                    JOptionPane.showMessageDialog(null, line);
                    poll.setEnabled(false);
                    submit.setEnabled(false);
                } else if (line.contains("WaitForNextRound")) {
                    SwingUtilities.invokeLater(() -> {
                        gameMessage.setForeground(Color.BLUE);
                        gameMessage.setText("You've joined mid-game. Wait for the next question.");
                        poll.setEnabled(false);
                        submit.setEnabled(false);
                        for (JRadioButton option : options) {
                            option.setEnabled(false);
                        }
                    });
                }
               
            }
        } catch (IOException e) {
            System.out.println("Disconnected from server.");
        }
    }

    private void displayQuestion(String fullText) {
        SwingUtilities.invokeLater(() -> {
            String[] lines = fullText.split("\n");
            if (lines.length < 6) return;
            question.setText("<html><b>" + lines[0] + "</b><br>" + lines[1] + "</html>");
            for (int i = 0; i < 4; i++) {
                options[i].setText("<html>" + lines[i + 2] + "</html>");
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
            byte[] buffer = "buzz".getBytes();
            DatagramSocket udpSocket = new DatagramSocket();
            InetAddress serverAddress = socket.getInetAddress();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, 1234);
            udpSocket.send(packet);
            udpSocket.close();
        } catch (IOException e) {
            System.out.println("Failed to send UDP poll.");
            e.printStackTrace();
        }
    }

    private void restartTimer(int duration) {
        if (clock != null) clock.cancel();
        clock = new TimerCode(duration);
        Timer t = new Timer();
        t.schedule(clock, 0, 1000);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();

        if (source instanceof JRadioButton) {
            for (int i = 0; i < options.length; i++) {
                if (options[i].isSelected()) {
                    selectedAnswer = String.valueOf((char) ('A' + i));
                    break;
                }
            }
        }

        if (source == submit) {
            if (!selectedAnswer.isEmpty()) {
                out.println(selectedAnswer);
                submit.setEnabled(false);
                for (JRadioButton option : options) {
                    option.setEnabled(false);
                }
            } else {
                JOptionPane.showMessageDialog(null, "Please select an answer first.");
            }
        }

        if (source == poll) {
            sendUDPBuzz();
            poll.setEnabled(false);
        }
    }

    public class TimerCode extends TimerTask {
        private int duration;

        public TimerCode(int duration) {
            this.duration = duration;
        }

        @Override
        public void run() {
            if (duration < 0) {
                timer.setText("Timer expired");
                submit.setEnabled(false);
                for (JRadioButton option : options) {
                    option.setEnabled(false);
                }
                out.println("Expired");  // Let server know time expired
                this.cancel();
                return;
            }

            timer.setForeground(duration < 6 ? Color.red : Color.black);
            timer.setText(duration + "");
            duration--;
            window.repaint();
        }
    }
} 