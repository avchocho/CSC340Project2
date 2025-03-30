import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.TimerTask;
import java.util.Timer;
import javax.swing.*;

//connects client to server using TCP, waits for trivia questions form server
//displays question and options, lets user select answer and submit
//sends selected answer to server, displays servers response (correct,wrong,score)


public class ClientWindow implements ActionListener {
    private JButton poll;
    private JButton submit;
    private JRadioButton options[];
    private ButtonGroup optionGroup;
    private JLabel question;
    private JLabel timer;
    private JLabel score;
    private TimerTask clock;
    private JFrame window;

    private static SecureRandom random = new SecureRandom();

    // Networking fields
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String selectedAnswer = "";  // Stores selected option like "A", "B", etc.

    public ClientWindow() {
        window = new JFrame("Florida Man Trivia");
        question = new JLabel("Waiting for question...");
        window.add(question);
        question.setBounds(10, 5, 350, 100);

        options = new JRadioButton[4];
        optionGroup = new ButtonGroup();
        for (int index = 0; index < options.length; index++) {
            options[index] = new JRadioButton("Option " + (index + 1));
            options[index].addActionListener(this);
            options[index].setBounds(10, 110 + (index * 20), 350, 20);
            options[index].setEnabled(false);
            window.add(options[index]);
            optionGroup.add(options[index]);
        }

        timer = new JLabel("TIMER");
        timer.setBounds(250, 250, 100, 20);
        clock = new TimerCode(30);
        Timer t = new Timer();
        t.schedule(clock, 0, 1000);
        window.add(timer);

        score = new JLabel("SCORE");
        score.setBounds(50, 250, 100, 20);
        window.add(score);

        poll = new JButton("Poll");
        poll.setBounds(10, 300, 100, 20);
        poll.addActionListener(this);
        poll.setEnabled(false); // currently unused
        window.add(poll);

        submit = new JButton("Submit");
        submit.setBounds(200, 300, 100, 20);
        submit.addActionListener(this);
        submit.setEnabled(false);
        window.add(submit);

        window.setSize(400, 400);
        window.setBounds(50, 50, 400, 400);
        window.setLayout(null);
        window.setVisible(true);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);

        try {
            socket = new Socket("localhost", 1234); // Replace localhost with server IP if remote
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
                    submit.setEnabled(true);
                } else if (line.equalsIgnoreCase("Correct!") || line.equalsIgnoreCase("Wrong!") || line.startsWith("Game Over")) {
                    JOptionPane.showMessageDialog(null, line);
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

            // Set the question number and actual question text in the top label (with line break)
            question.setText("<html><b>" + lines[0] + "</b><br>" + lines[1] + "</html>");

            // Set the radio button labels to options A–D
            for (int i = 0; i < 4; i++) {
                options[i].setText("<html>" + lines[i + 2] + "</html>");
                options[i].setEnabled(true);
                options[i].setSelected(false);
            }

            optionGroup.clearSelection();
            selectedAnswer = "";
            //submit.setEnabled(false);
        });
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
            // Future: send UDP buzz here
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
                window.repaint();
                this.cancel();
                return;
            }

            if (duration < 6)
                timer.setForeground(Color.red);
            else
                timer.setForeground(Color.black);

            timer.setText(duration + "");
            duration--;
            window.repaint();
        }
    }

    public static void main(String[] args) {
        new ClientWindow();
    }
}







//import java.awt.Color;
//import java.awt.event.ActionEvent;
//import java.awt.event.ActionListener;
//import java.security.SecureRandom;
//import java.util.TimerTask;
//import java.util.Timer;
//import javax.swing.*;
//
//public class ClientWindow implements ActionListener
//{
//	private JButton poll;
//	private JButton submit;
//	private JRadioButton options[];
//	private ButtonGroup optionGroup;
//	private JLabel question;
//	private JLabel timer;
//	private JLabel score;
//	private TimerTask clock;
//	
//	private JFrame window;
//	
//	private static SecureRandom random = new SecureRandom();
//	
//	// write setters and getters as you need
//	
//	public ClientWindow()
//	{
//		JOptionPane.showMessageDialog(window, "This is a trivia game");
//		
//		window = new JFrame("Trivia");
//		question = new JLabel("Q1. This is a sample question"); // represents the question
//		window.add(question);
//		question.setBounds(10, 5, 350, 100);;
//		
//		options = new JRadioButton[4];
//		optionGroup = new ButtonGroup();
//		for(int index=0; index<options.length; index++)
//		{
//			options[index] = new JRadioButton("Option " + (index+1));  // represents an option
//			// if a radio button is clicked, the event would be thrown to this class to handle
//			options[index].addActionListener(this);
//			options[index].setBounds(10, 110+(index*20), 350, 20);
//			window.add(options[index]);
//			optionGroup.add(options[index]);
//		}
//
//		timer = new JLabel("TIMER");  // represents the countdown shown on the window
//		timer.setBounds(250, 250, 100, 20);
//		clock = new TimerCode(30);  // represents clocked task that should run after X seconds
//		Timer t = new Timer();  // event generator
//		t.schedule(clock, 0, 1000); // clock is called every second
//		window.add(timer);
//		
//		
//		score = new JLabel("SCORE"); // represents the score
//		score.setBounds(50, 250, 100, 20);
//		window.add(score);
//
//		poll = new JButton("Poll");  // button that use clicks/ like a buzzer
//		poll.setBounds(10, 300, 100, 20);
//		poll.addActionListener(this);  // calls actionPerformed of this class
//		window.add(poll);
//		
//		submit = new JButton("Submit");  // button to submit their answer
//		submit.setBounds(200, 300, 100, 20);
//		submit.addActionListener(this);  // calls actionPerformed of this class
//		window.add(submit);
//		
//		
//		window.setSize(400,400);
//		window.setBounds(50, 50, 400, 400);
//		window.setLayout(null);
//		window.setVisible(true);
//		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//		window.setResizable(false);
//	}
//
//	// this method is called when you check/uncheck any radio button
//	// this method is called when you press either of the buttons- submit/poll
//	@Override
//	public void actionPerformed(ActionEvent e)
//	{
//		System.out.println("You clicked " + e.getActionCommand());
//		
//		// input refers to the radio button you selected or button you clicked
//		String input = e.getActionCommand();  
//		switch(input)
//		{
//			case "Option 1":	// Your code here
//								break;
//			case "Option 2":	// Your code here
//								break;
//			case "Option 3":	// Your code here
//								break;
//			case "Option 4":	// Your code here
//								break;
//			case "Poll":		// Your code here
//								break;
//			case "Submit":		// Your code here
//								break;
//			default:
//								System.out.println("Incorrect Option");
//		}
//		
//		// test code below to demo enable/disable components
//		// DELETE THE CODE BELOW FROM HERE***
//		if(poll.isEnabled())
//		{
//			poll.setEnabled(false);
//			submit.setEnabled(true);
//		}
//		else
//		{
//			poll.setEnabled(true);
//			submit.setEnabled(false);
//		}
//		
//		question.setText("Q2. This is another test problem " + random.nextInt());
//		
//		// you can also enable disable radio buttons
////		options[random.nextInt(4)].setEnabled(false);
////		options[random.nextInt(4)].setEnabled(true);
//		// TILL HERE ***
//		
//	}
//	
//	// this class is responsible for running the timer on the window
//	public class TimerCode extends TimerTask
//	{
//		private int duration;  // write setters and getters as you need
//		public TimerCode(int duration)
//		{
//			this.duration = duration;
//		}
//		@Override
//		public void run()
//		{
//			if(duration < 0)
//			{
//				timer.setText("Timer expired");
//				window.repaint();
//				this.cancel();  // cancel the timed task
//				return;
//				// you can enable/disable your buttons for poll/submit here as needed
//			}
//			
//			if(duration < 6)
//				timer.setForeground(Color.red);
//			else
//				timer.setForeground(Color.black);
//			
//			timer.setText(duration+"");
//			duration--;
//			window.repaint();
//		}
//	}
//	
//}