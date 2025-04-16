import java.io.*;
import java.net.*;

public class ClientThread implements Runnable {
    private final Socket socket;
    private final int clientID;
    private BufferedReader in;
    private PrintWriter out;
    private String correctAnswer;
    private int score;
    private boolean canAnswer;
    private boolean joinedMidGame = false;
    private int unansweredCount = 0;

    //initializes client state 
    public ClientThread(Socket socket, int id) {
        this.socket = socket;
        this.clientID = id;
        this.score = 0;
        this.canAnswer = false;

        try {
        	//set up input and output communication
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            
            //send welcome and initial score
            sendMessage("Welcome Client-" + clientID);
            sendMessage("score " + score);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //getters and setters

    public int getClientID() {
        return clientID;
    }

    public Socket getSocket() {
        return socket;
    }

    public int getScore() {
        return score;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer.toUpperCase();
    }

    public void setCanAnswer(boolean canAnswer) {
        this.canAnswer = canAnswer;
    }

    public boolean getCanAnswer() {
        return canAnswer;
    }

    public void increaseScore(int points) {
        score += points;
    }

    public void decreaseScore(int points) {
        score -= points;
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    public void setJoinedMidGame(boolean joined) {
        this.joinedMidGame = joined;
    }

    public boolean hasJoinedMidGame() {
        return joinedMidGame;
    }
    
    public int getUnansweredCount() {
    	return unansweredCount;
    }

    public void incrementUnanswered() {
    	unansweredCount++;
    }
    
    public void resetUnansweredCount() {
    	unansweredCount = 0;
    }
    //close this client's resources
    public void close() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
            System.out.println("Client-" + clientID + " disconnected.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //evaluates and processes a submitted answer
    private void checkAnswer(String answer) {
        if (!canAnswer) {
            sendMessage("You are not allowed to answer.");
            return;
        }

        String trimmed = answer.trim().toUpperCase();
        resetUnansweredCount();//reset missed question count since the player responded
        
        //check correctness
        if (trimmed.equals(correctAnswer)) {
            increaseScore(10);
            sendMessage("correct " + score);
            System.out.println("Client-" + clientID + " answered correctly.");
        } else {
            decreaseScore(10);
            sendMessage("wrong " + score);
            System.out.println("Client-" + clientID + " answered incorrectly.");
        }

        canAnswer = false; //client used their turn

        //move on to next question
        try {
            TriviaServer.moveAllToNextQuestion();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //main client thread logic
    @Override
    public void run() {
        try {
        	//handle clients who join mid game by making them wait
            if (joinedMidGame) {
                sendMessage("WaitForNextRound");
                int waitAt = TriviaServer.getCurrentQuestionIndex();
                while (TriviaServer.getCurrentQuestionIndex() == waitAt) {
                    Thread.sleep(800);
                }
                joinedMidGame = false; //done waiting
            }

            //continously listens for input from client
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("Client-" + clientID + ": " + message);

                //handle timeout message
                //could delete since its being dealt in server side now?
                if (message.equalsIgnoreCase("Expired")) {
                    if (canAnswer) {
                        canAnswer = false;
                        score -= 20;
                        //unansweredCount++;
                        sendMessage("noAnswerPenalty " + score);
                        System.out.println("Client-" + clientID + " did not answer. -20 points.");

                        //kick client after 2 missed answers 
//                        if (unansweredCount >= 2) {
//                            sendMessage("You have been removed for not answering twice.");
//                            System.out.println("Client-" + clientID + " kicked for inactivity.");
//                            TriviaServer.removeClient(this);
//                            close();
//                            return;
//                        }
                    }
                    //let the server handle timeout logic
                    TriviaServer.clientOutOfTime(this);
                }

                //handle regular answer submission
                else {
                    checkAnswer(message);
                }
            }

            // readline returned null - client disconnected
            //debug statement
            //System.out.println("Client-" + clientID + " readLine() returned null."); 
            TriviaServer.removeClient(this);
        

        } catch (IOException | InterruptedException e) {
            System.out.println("Client-" + clientID + " disconnected (exception).");
            try {
                TriviaServer.removeClient(this);
                close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

	
} 