import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;


//controls the game logic and flow
//loads questions, accepts client connections, runs game loop, tracks scores
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

// Controls the game logic and flow
public class TriviaServer {
    private static final int PORT = 1234;
    private static final List<Question> questions = new ArrayList<>();
    private static final List<ClientHandler> clients = new ArrayList<>();
    private static final Map<String, Integer> scores = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        loadQuestions();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Trivia Server started on port " + PORT);

            ExecutorService pool = Executors.newCachedThreadPool();

            // Accept clients
            while (clients.size() < 1) { // Change this to desired number of players
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, clients.size());
                clients.add(handler);
                scores.put("Client-" + handler.getClientID(), 0);
                pool.execute(handler);
                System.out.println("Client-" + handler.getClientID() + " connected.");
            }

            System.out.println("Starting Trivia Game!");
            runTriviaGame();

            System.out.println("Final Scores:");
            scores.forEach((name, score) -> System.out.println(name + ": " + score));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void runTriviaGame() {
        for (int i = 0; i < questions.size(); i++) {
        	Question q = questions.get(i);

            // Broadcast 6 lines (1 question block) to all clients
            //broadcast("Question " + (i + 1) + ":\n" + q.getFormattedQuestion());
        	for (ClientHandler client : clients) {
        	    client.sendMessage(q.getQuestionNumber() + ":");       // Line 0
        	    client.sendMessage(q.getQuestionText());               // Line 1
        	    client.sendMessage("A. " + q.getOptions()[0]);         // Line 2
        	    client.sendMessage("B. " + q.getOptions()[1]);         // Line 3
        	    client.sendMessage("C. " + q.getOptions()[2]);         // Line 4
        	    client.sendMessage("D. " + q.getOptions()[3]);         // Line 5
        	    client.sendMessage("Your Answer:");                    // Line 6 (trigger for enabling submit)
        	}


//            // Also send the question line-by-line for GUI support
//            for (ClientHandler client : clients) {
//                client.sendMessage(q.getQuestionNumber() + ":");        // Line 0
//                client.sendMessage(q.getQuestionText());               // Line 1
//                client.sendMessage("A. " + q.getOptions()[0]);         // Line 2
//                client.sendMessage("B. " + q.getOptions()[1]);         // Line 3
//                client.sendMessage("C. " + q.getOptions()[2]);         // Line 4
//                client.sendMessage("D. " + q.getOptions()[3]);         // Line 5
//                client.sendMessage("Your Answer:");                    // Line 6
//            }

            // Collect and score answers
            for (ClientHandler client : clients) {
                String answer = client.receiveAnswer();
                if (answer == null || answer.isEmpty()) continue;

                char selected = answer.trim().toUpperCase().charAt(0);
                if (selected == q.getCorrectAnswer()) {
                    scores.put("Client-" + client.getClientID(),
                            scores.get("Client-" + client.getClientID()) + 10);
                    client.sendMessage("Correct! +10 points.");
                } else {
                    scores.put("Client-" + client.getClientID(),
                            scores.get("Client-" + client.getClientID()) - 10);
                    client.sendMessage("Wrong! -10 points.");
                }
            }
        }

        // End game
        for (ClientHandler client : clients) {
            client.sendMessage("Game Over! Your final score: " + scores.get("Client-" + client.getClientID()));
            client.close();
        }
    }

    private static void broadcast(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    private static void loadQuestions() {
        try (BufferedReader br = new BufferedReader(new FileReader("Questions.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
            	questions.add(new Question(line));
            }
        } catch (IOException e) {
            System.err.println("Could not load questions.");
            e.printStackTrace();
        }
    }
}
