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

    public ClientThread(Socket socket, int id) {
        this.socket = socket;
        this.clientID = id;
        this.score = 0;
        this.canAnswer = false;

        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            sendMessage("Welcome Client-" + clientID);
            sendMessage("score " + score);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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
        this.correctAnswer = correctAnswer.trim().toUpperCase();
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

    public void close() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkAnswer(String answer) {
        if (!canAnswer) {
            sendMessage("You are not allowed to answer.");
            return;
        }

        String trimmed = answer.trim().toUpperCase();
        if (trimmed.equals(correctAnswer)) {
            increaseScore(10);
            sendMessage("correct " + score);
            System.out.println("✅ Client-" + clientID + " answered correctly.");
        } else {
            decreaseScore(10);
            sendMessage("wrong " + score);
            System.out.println("❌ Client-" + clientID + " answered incorrectly.");
        }

        try {
            TriviaServer.moveAllToNextQuestion();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("📥 From Client-" + clientID + ": " + message);

                if (message.startsWith("Score")) {
                    // Server penalized client for time-out
                    int penalty = Integer.parseInt(message.substring("Score".length()).trim());
                    decreaseScore(penalty);
                    sendMessage("score " + score);
                    TriviaServer.moveAllToNextQuestion();
                } else if (message.equalsIgnoreCase("Expired")) {
                    TriviaServer.clientOutOfTime(this);
                } else {
                    checkAnswer(message);
                }
            }
        } catch (IOException e) {
            System.out.println("⚠ Client-" + clientID + " disconnected.");
            try {
                TriviaServer.removeClient(this);
                close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
