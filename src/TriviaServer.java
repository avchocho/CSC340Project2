import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;


public class TriviaServer {
    private static final int TCP_PORT = 1234;
    private static final int UDP_PORT = 1235;

    // List of trivia questions
    private static final List<Question> questions = new ArrayList<>();
    // Connected client threads
    private static final List<ClientThread> clients = new ArrayList<>();
    // Queue for buzzed-in clients
    public static final Queue<ClientThread> buzzQueue = new LinkedList<>();
    // Thread pool to manage clients
    private static final ExecutorService pool = Executors.newCachedThreadPool();

    private static int currentQuestionIndex = 0;
    private static boolean receivingBuzzes = true;
    private static boolean hasPrintedWinners = false;
    private static ServerSocket serverSocket;
    private static int nextClientID = 0;

    private static Timer activeTimer = new Timer();

    public static void main(String[] args) {
        loadQuestions();

        try {
            serverSocket = new ServerSocket(TCP_PORT);
            System.out.println("Trivia Server started on port " + TCP_PORT);

            // Start thread to listen for UDP buzzes
            new UDPBuzzThread().start();

            // Admin command listener thread (e.g., for kicking clients)
            new Thread(() -> {
                Scanner scanner = new Scanner(System.in);
                while (true) {
                    String command = scanner.nextLine();
                    if (command.startsWith("kill ")) {
                        try {
                            int id = Integer.parseInt(command.split(" ")[1]);
                            for (ClientThread client : new ArrayList<>(clients)) {
                                if (client.getClientID() == id) {
                                    client.sendMessage("killswitch");
                                    removeClient(client);
                                    System.out.println("Client-" + id + " was kicked by admin.");
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("Invalid kick command.");
                        }
                    }
                }
            }).start();

            // Accept incoming client connections
            new Thread(() -> {
                while (true) {
                    try {
                        Socket socket = serverSocket.accept();
                        ClientThread client = new ClientThread(socket, nextClientID++);

                        synchronized (clients) {
                            clients.add(client);
                        }
                        if (currentQuestionIndex > 0) {
                            client.setJoinedMidGame(true);
                            client.sendMessage("WaitForNextRound");
                        }

                        pool.execute(client);
                        System.out.println("Client-" + client.getClientID() + " connected.");
                    } catch (IOException e) {
                        break;
                    }
                }
            }).start();

            // Wait 15 seconds for players to join before starting the game
            System.out.println("Waiting 15 seconds for clients to join..");
            Thread.sleep(15000);

            synchronized (clients) {
                if (clients.size() < 2) { 
                    System.out.println("Not enough clients joined. Server shutting down.");
                    for (ClientThread client: new ArrayList<>(clients)) {
                        client.sendMessage("not_enough_players");
                    }
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        serverSocket.close();
                    }
                    pool.shutdownNow();
                    System.exit(0);
                } else {
                    System.out.println("Starting Trivia Game!");
                    sendNextQuestionToAll();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Sends the next trivia question to all clients
    public static void sendNextQuestionToAll() throws IOException {
        if (currentQuestionIndex >= questions.size()) {
            endGame();
            return;
        }

        receivingBuzzes = true;
        buzzQueue.clear();

        Question q = questions.get(currentQuestionIndex);
        System.out.println("\n " + q.getQuestionNumber() + ": " + q.getQuestionText());

        for (ClientThread client : clients) {
            client.setCanAnswer(false);
            client.setCorrectAnswer(String.valueOf(q.getCorrectAnswer()));

            client.sendMessage(q.getQuestionNumber() + ":");
            client.sendMessage(q.getQuestionText());
            client.sendMessage("A. " + q.getOptions()[0]);
            client.sendMessage("B. " + q.getOptions()[1]);
            client.sendMessage("C. " + q.getOptions()[2]);
            client.sendMessage("D. " + q.getOptions()[3]);
            client.sendMessage("Your Answer:");
        }

        currentQuestionIndex++;

        // Start 15-second timer for players to buzz in
        startTimer(15, () -> {
            receivingBuzzes = false;

            if (!buzzQueue.isEmpty()) {
                ClientThread winner;
                synchronized (buzzQueue) {
                    winner = buzzQueue.poll();
                }

                winner.setCanAnswer(true);
                winner.sendMessage("ACK");
                System.out.println("Client-" + winner.getClientID() + " buzzed first and may answer.");

                for (ClientThread client : clients) {
                    if (client != winner) {
                        client.sendMessage("NAK");
                    }
                }

                // Start 10-second timer for answering
                startTimer(10, () -> {
                    try {
                        clientOutOfTime(winner);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

            } else {
                System.out.println("No one buzzed in. Skipping to next question.");
                for (ClientThread client : clients) {
                    client.sendMessage("Time expired");
                }

                try {
                    sendNextQuestionToAll();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // Ends the game and displays final scores
    private static void endGame() throws IOException {
        if (hasPrintedWinners) return;
        hasPrintedWinners = true;

        System.out.println("\n Game Over. Final Scores:");
        clients.sort((a, b) -> b.getScore() - a.getScore());
        
        //scoreboard 
        StringBuilder scoreboard = new StringBuilder("SCOREBOARD|");
        for (ClientThread client : clients) {
            scoreboard.append("Client-")
                      .append(client.getClientID())
                      .append(":")
                      .append(client.getScore())
                      .append(";");
        }

        //sends final scores of clients to each client 
        for (ClientThread client : new ArrayList<>(clients)) {
            try {
                client.sendMessage("FINAL_SCORE:" + client.getScore());
                client.sendMessage(scoreboard.toString()); //sends full scoreboard i hope 
                client.sendMessage("Game Over!");
                System.out.println("Client " + client.getClientID() + ": " + client.getScore());
            } catch (Exception e) {
                System.out.println("Error sending final score to Client-" + client.getClientID() + ": " + e.getMessage());
            }
        }

        try {
            Thread.sleep(1000); // Allow time for clients to receive messages
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }

        pool.shutdownNow();
        System.out.println("Server shutting down");
        System.exit(0);
    }

    // Loads questions from a text file
    private static void loadQuestions() {
        try (InputStream is = TriviaServer.class.getResourceAsStream("/Questions.txt");
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {

            String line;
            while ((line = br.readLine()) != null) {
                questions.add(new Question(line));
            }

        } catch (IOException | NullPointerException e) {
            System.out.println("Failed to load Questions.txt from JAR");
            e.printStackTrace();
        }
    }

    // Thread for listening to UDP buzzes from clients
    private static class UDPBuzzThread extends Thread {
        private final byte[] buffer = new byte[256];

        public void run() {
            try (DatagramSocket socket = new DatagramSocket(UDP_PORT)) {
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength()).trim();
                    InetAddress address = packet.getAddress();

                    if (receivingBuzzes && message.equalsIgnoreCase("buzz")) {
                        ClientThread client = findClientByAddress(address);
                        if (client != null) {
                            synchronized (TriviaServer.buzzQueue) {
                                if (!TriviaServer.buzzQueue.contains(client)) {
                                    TriviaServer.buzzQueue.offer(client);
                                    System.out.println("Client-" + client.getClientID() + " buzzed.");
                                }
                            }
                        } else {
                            System.out.println("No matching client found for UDP address: " + address);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("UDP Thread error: " + e.getMessage());
            }
        }

        private ClientThread findClientByAddress(InetAddress address) {
            synchronized (clients) {
                for (ClientThread client : clients) {
                    if (client.getSocket().getInetAddress().equals(address)) {
                        return client;
                    }
                }
            }
            return null;
        }
    }

    // Starts a countdown timer and broadcasts time left to clients
    public static void startTimer(int seconds, Runnable onExpire) {
        activeTimer.cancel();
        activeTimer = new Timer();

        activeTimer.scheduleAtFixedRate(new TimerTask() {
            int timeLeft = seconds;

            @Override
            public void run() {
                for (ClientThread client : clients) {
                    client.sendMessage("TIMER:" + timeLeft);
                }

                if (timeLeft <= 0) {
                    cancel();
                    onExpire.run();
                }

                timeLeft--;
            }
        }, 0, 1000);
    }

    // Removes a client and shuts down server if no clients remain
    public static void removeClient(ClientThread client) throws IOException {
        clients.remove(client);
        System.out.println("Removing Client-" + client.getClientID());
        client.close();

        if(clients.isEmpty()) {
            System.out.println("All clients disconnected. Shutting down server...");
            serverSocket.close();
            pool.shutdownNow();
            System.out.println("Server shutting down");
            System.exit(0);
        }
    }

    public static int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    // Called when a client runs out of time to answer
    public static void clientOutOfTime(ClientThread client) throws IOException {
    	if (client.getCanAnswer()) {
    		client.setCanAnswer(false);
    		client.decreaseScore(20);
    		client.sendMessage("noAnswerPenalty " + client.getScore());
    		System.out.println("Client -" + client.getClientID() + " buzzed in but didn't answer. -20");
    	}
    	
    	client.sendMessage("Time expired");
    	sendNextQuestionToAll();
    }

    // Handles timer after answer submission (5 seconds)
    public static void handleSubmission() {
        startTimer(5, () -> {
            for (ClientThread client : clients) {
                client.sendMessage("You may poll again.");
                client.sendMessage("UNLOCK_POLL");
            }
        });
    }

    // Advances the game to the next question
    public static void moveAllToNextQuestion() throws IOException {
        sendNextQuestionToAll();
    }
}
