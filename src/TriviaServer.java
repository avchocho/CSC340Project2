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
    private static int currentAttemptCount = 0;

    private static Timer activeTimer = new Timer();

    public static void main(String[] args) {
        loadQuestions();

        try {
            serverSocket = new ServerSocket(TCP_PORT);
            System.out.println("Trivia Server started on port " + TCP_PORT);

            new UDPBuzzThread().start();

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

            System.out.println("waiting 15 seconds for clients to join..");
            Thread.sleep(15000);

            synchronized (clients) {
                if (clients.size() < 2) {
                    System.out.println("Not enough clients joined. Server shutting down.");
                    for (ClientThread client : new ArrayList<>(clients)) {
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

    public static void sendNextQuestionToAll() throws IOException {
        if (currentQuestionIndex >= questions.size()) {
            endGame();
            return;
        }

        receivingBuzzes = true;
        buzzQueue.clear();
        currentAttemptCount = 0;

        Question q = questions.get(currentQuestionIndex);
        System.out.println("\n❓ " + q.getQuestionNumber() + ": " + q.getQuestionText());

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

        startTimer(15, () -> {
            receivingBuzzes = false;
            allowNextBuzzedClient();
        });
    }

    public static void allowNextBuzzedClient() {
        if (currentAttemptCount >= 2) {
            System.out.println("❌ Max attempts reached. Moving to next question.");
            for (ClientThread client : clients) {
                client.sendMessage("Time expired");
            }
            try {
                sendNextQuestionToAll();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        ClientThread next;
        synchronized (buzzQueue) {
            next = buzzQueue.poll();
        }

        if (next != null) {
            currentAttemptCount++;
            next.setCanAnswer(true);
            next.sendMessage("ACK");
            next.sendMessage("YOUR_TURN");
            System.out.println("🎯 Client-" + next.getClientID() + " buzzed and may answer.");

            for (ClientThread client : clients) {
                if (client != next) {
                    client.sendMessage("NAK");
                }
            }

            startTimer(10, () -> {
                try {
                    clientOutOfTime(next);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } else {
            System.out.println("❌ No more buzzers. Moving to next question.");
            for (ClientThread client : clients) {
                client.sendMessage("Time expired");
            }
            try {
                sendNextQuestionToAll();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


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
                            synchronized (buzzQueue) {
                                if (!buzzQueue.contains(client)) {
                                    buzzQueue.offer(client);
                                    System.out.println("✅ Client-" + client.getClientID() + " buzzed.");
                                }
                            }
                        } else {
                            System.out.println("⚠️ No matching client found for UDP address: " + address);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("❌ UDP Thread error: " + e.getMessage());
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

    public static void removeClient(ClientThread client) throws IOException {
        clients.remove(client);
        System.out.println("Removing Client-" + client.getClientID());
        client.close();

        if (clients.isEmpty()) {
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

    public static void clientOutOfTime(ClientThread client) throws IOException {
        client.sendMessage("Time expired");
        client.incrementUnanswered();

        if (client.getUnansweredCount() >= 2) {
            System.out.println("Client-" + client.getClientID() + " terminated (missed 2 questions in a row)");
            client.sendMessage("killswitch");
            removeClient(client);
        }
        sendNextQuestionToAll();
    }

    public static void handleSubmission() {
        startTimer(5, () -> {
            for (ClientThread client : clients) {
                client.sendMessage("You may poll again.");
                client.sendMessage("UNLOCK_POLL");
            }
        });
    }

    public static void moveAllToNextQuestion() throws IOException {
        sendNextQuestionToAll();
    }

    private static void endGame() throws IOException {
        if (hasPrintedWinners) return;
        hasPrintedWinners = true;

        System.out.println("\n🏁 Game Over. Final Scores:");
        clients.sort((a, b) -> b.getScore() - a.getScore());

        for (ClientThread client : new ArrayList<>(clients)) {
            client.sendMessage("Game Over! Your final score: " + client.getScore());
            System.out.println("Client " + client.getClientID() + ": " + client.getScore());
            removeClient(client);
        }

        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }

        pool.shutdown();
        System.out.println("Server shutting down");
        System.exit(0);
    }
}