import java.io.*;
import java.net.*;

public class ClientThread implements Runnable, Comparable<ClientThread> {
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private String correctAnswer;
    private int score = 0;
    private boolean canAnswer = false;
    private boolean joinedMidGame = false;
    private final int clientID;

    public ClientThread(Socket socket, int id) throws IOException {
        this.socket = socket;
        this.clientID = id;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
        sendMessage("score " + score);
    }

    public void run() {
        try {
            if (joinedMidGame) {
                sendMessage("WaitForNextRound");
                int waitAt = TriviaServer.getCurrentQuestionIndex();
                while (TriviaServer.getCurrentQuestionIndex() == waitAt) {
                    Thread.sleep(500);
                }
                joinedMidGame = false;
            }

            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("📥 From Client-" + clientID + ": " + message);

                if (message.equalsIgnoreCase("Expired")) {
                    TriviaServer.clientOutOfTime(this);
                } else if (message.toLowerCase().startsWith("score")) {
                    int penalty = Integer.parseInt(message.substring(5).trim());
                    score -= penalty;
                    sendMessage("score " + score);
                    TriviaServer.moveAllToNextQuestion();
                } else {
                    checkAnswer(message.trim().toUpperCase());
                }
            }
        } catch (Exception e) {
            System.out.println("⚠ Client-" + clientID + " disconnected.");
            try {
                TriviaServer.removeClient(this);
                close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void checkAnswer(String answer) {
        if (!canAnswer) {
            sendMessage("You are not allowed to answer.");
            return;
        }

        if (answer.equals(correctAnswer)) {
            score += 10;
            sendMessage("correct " + score);
            System.out.println("✅ Client-" + clientID + " answered correctly.");
        } else {
            score -= 10;
            sendMessage("wrong " + score);
            System.out.println("❌ Client-" + clientID + " answered incorrectly.");
        }

        try {
            TriviaServer.moveAllToNextQuestion();
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    public int getScore() {
        return score;
    }

    public void decreaseScore(int points) {
        this.score -= points;
    }

    public Socket getSocket() {
        return socket;
    }

    public int getClientID() {
        return clientID;
    }

    public void sendMessage(String msg) {
        out.println(msg);
    }

    public void setJoinedMidGame(boolean joined) {
        this.joinedMidGame = joined;
    }

    public boolean hasJoinedMidGame() {
        return joinedMidGame;
    }

    public void close() throws IOException {
        in.close();
        out.close();
        socket.close();
    }

    @Override
    public int compareTo(ClientThread other) {
        return Integer.compare(other.score, this.score); // descending
    }
}
