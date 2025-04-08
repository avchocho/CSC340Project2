import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class TriviaServer {
    private static final int TCP_PORT = 1234;
    private static final int UDP_PORT = 1235;
    private static final List<Question> questions = new ArrayList<>();
    private static final List<ClientThread> clients = new ArrayList<>();
    public static final Queue<ClientThread> buzzQueue = new LinkedList<>();
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
            
            //start separate thread to listen for UDP buzzes
            new UDPBuzzThread().start();
            
            //thread to handle manual kick a specific client 
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
            
            //thread to handle new client connections 
            new Thread(() -> {
                while (true) {
                    try {
                        Socket socket = serverSocket.accept();
                        ClientThread client = new ClientThread(socket, nextClientID++);

                        synchronized (clients) {
                            clients.add(client);
                        }
                        // if game already started, tell the new client to wait 
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
            
            //delay to allow clients to join before starting the game
            System.out.println("Waiting 15 seconds for clients to join..");
            Thread.sleep(15000);
            
       
            //check if enough clients joined
            synchronized (clients) {
                if (clients.size() < 2) {
                    System.out.println("Not enough clients joined. Server shutting down.");
                    for (ClientThread client: new ArrayList <>(clients)) {
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

    //broadcast new question to all clients
    public static void sendNextQuestionToAll() throws IOException {
        if (currentQuestionIndex >= questions.size()) {
            endGame();
            return;
        }

        receivingBuzzes = true;
        buzzQueue.clear(); // reset buzz queue for the round

        Question q = questions.get(currentQuestionIndex);
        System.out.println("\n❓ " + q.getQuestionNumber() + ": " + q.getQuestionText());

        //send questions and options to all clients 
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

        // Start 15 second poll timer
        startTimer(15, () -> {
            receivingBuzzes = false;

            if (!buzzQueue.isEmpty()) {
                ClientThread winner;
                synchronized (buzzQueue) {
                    winner = buzzQueue.poll(); // get first buzzer
                }

                winner.setCanAnswer(true);
                winner.sendMessage("ACK");
                System.out.println("Client-" + winner.getClientID() + " buzzed first and may answer.");

                for (ClientThread client : clients) {
                    if (client != winner) {
                        client.sendMessage("NAK");
                    }
                }

                // Start 10 second answer timer
                startTimer(10, () -> {
                    try {
                        clientOutOfTime(winner);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

            } else {
            	//if no one buzzed, move on to next question
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


    //ends the game and shows the final scores
    private static void endGame() throws IOException {
        if (hasPrintedWinners) return;
        hasPrintedWinners = true;

        System.out.println("\n Game Over. Final Scores:");
        clients.sort((a, b) -> b.getScore() - a.getScore());

        for (ClientThread client : new ArrayList<>(clients)) {
            client.sendMessage("Game Over! Your final score: " + client.getScore());
            System.out.println("Client " + client.getClientID() + ": " + client.getScore());
            removeClient(client);  // this calls close() which prints disconnect
        }
        //shutting down server after game ends
        if(serverSocket != null && !serverSocket.isClosed()) {
        	serverSocket.close(); //stop accepting new clients
        }
        
        pool.shutdown();//stop all running client threads
        System.out.println("Server shutting down");
        System.exit(0);
    }
    //load questions from text file
    private static void loadQuestions() {
        try (BufferedReader br = new BufferedReader(new FileReader("Questions.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                questions.add(new Question(line));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //thread that listens for buzzes thru UDP
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

        //match UDP packet to known client by IP address
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
    //starts a countdown timer for poll/answer
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

    //remove clients and shuts down if no clients left
    public static void removeClient(ClientThread client) throws IOException {
        clients.remove(client);
        System.out.println("Removing Client-" + client.getClientID());
        client.close();
        
        //if all clients disconnected, server shouldn't be listening infinitely and shut down
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

   //called if client doesn't answer in time
    public static void clientOutOfTime(ClientThread client) throws IOException {
        client.sendMessage("Time expired");
        client.incrementUnanswered();
        
        if(client.getUnansweredCount() >= 2) {
        	System.out.println("Client-" + client.getClientID() + " terminated (missed 2 questions in a row)");
        	client.sendMessage("killswitch");
        	removeClient(client);
        } 
        	sendNextQuestionToAll(); //move on to next question even if client was removed
        }
    
//unlocks poll after 5 second delay and after answer submission
    public static void handleSubmission() {
        startTimer(5, () -> {
            for (ClientThread client : clients) {
                client.sendMessage("You may poll again.");
                client.sendMessage("UNLOCK_POLL");
            }
        });
    }

    //manually trigger next question if needed
    public static void moveAllToNextQuestion() throws IOException {
        sendNextQuestionToAll();
    }
}
