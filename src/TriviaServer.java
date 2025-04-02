import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class TriviaServer {
    private static final int TCP_PORT = 1234;
    private static final int UDP_PORT = 1235;

    private static final List<Question> questions = new ArrayList<>();
    private static final List<ClientThread> clients = new ArrayList<>();
    private static final ExecutorService pool = Executors.newCachedThreadPool();
    private static final Queue<String> buzzQueue = new ConcurrentLinkedQueue<>();

    private static int currentQuestionIndex = 0;
    private static boolean receivingBuzzes = true;
    private static boolean hasPrintedWinners = false;
    private static Timer roundTimer;

    public static void main(String[] args) {
        loadQuestions();

        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            System.out.println("📡 Trivia Server started on port " + TCP_PORT);

            new UDPBuzzThread().start();

            // Accept clients in a background thread
            new Thread(() -> {
                while (true) {
                    try {
                        Socket socket = serverSocket.accept();
                        ClientThread client = new ClientThread(socket, clients.size());

                        if (currentQuestionIndex > 0) {
                            client.setJoinedMidGame(true);
                            client.sendMessage("WaitForNextRound");
                        }

                        synchronized (clients) {
                            clients.add(client);
                        }
                        pool.execute(client);
                        System.out.println("Client-" + client.getClientID() + " connected.");
                    } catch (IOException e) {
                        e.printStackTrace();
                        break;
                    }
                }
            }).start();

            // Wait for at least 2 clients to join
            while (clients.size() < 2) {
                Thread.sleep(500);
            }

            System.out.println("✅ Starting Trivia Game!");
            sendNextQuestionToAll();

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
        Question q = questions.get(currentQuestionIndex);
        System.out.println("\n❓ Question " + (currentQuestionIndex + 1) + ": " + q.getQuestionText());

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

        startRoundTimer();
        currentQuestionIndex++;
    }

    public static void moveAllToNextQuestion() throws IOException {
        if (roundTimer != null) roundTimer.cancel();
        sendNextQuestionToAll();
    }

    public static synchronized void removeClient(ClientThread client) throws IOException {
        clients.remove(client);
        client.close();
    }

    public static int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public static void clientOutOfTime(ClientThread client) throws IOException {
        System.out.println("⏰ Client-" + client.getClientID() + " ran out of time!");
        client.sendMessage("Time's up! -20 points.");
        client.setCanAnswer(false);
        client.decreaseScore(20);
        client.sendMessage("score " + client.getScore());
        moveAllToNextQuestion();
    }

    private static void startRoundTimer() {
        if (roundTimer != null) roundTimer.cancel();

        roundTimer = new Timer();
        roundTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("⏱ No buzz received in time. Skipping...");
                try {
                    moveAllToNextQuestion();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 15000); // 15-second round timeout
    }

    private static void endGame() throws IOException {
        if (hasPrintedWinners) return;
        hasPrintedWinners = true;

        System.out.println("\n🏁 Game Over! Final Scores:");
        clients.sort(Comparator.comparingInt(ClientThread::getScore).reversed());

        for (ClientThread client : clients) {
            client.sendMessage("Game Over! Your final score: " + client.getScore());
            System.out.println("Client-" + client.getClientID() + ": " + client.getScore());
            client.close();
        }

        pool.shutdown();
    }

    private static void loadQuestions() {
        try (BufferedReader br = new BufferedReader(new FileReader("Questions.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                questions.add(new Question(line));
            }
        } catch (IOException e) {
            System.err.println("⚠ Could not load questions.");
            e.printStackTrace();
        }
    }

    // === UDP Buzz Thread ===
    private static class UDPBuzzThread extends Thread {
        private final byte[] buffer = new byte[256];

        public void run() {
            try (DatagramSocket socket = new DatagramSocket(UDP_PORT)) {
                System.out.println("📡 UDP Buzz thread listening on port " + UDP_PORT);

                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());
                    InetAddress address = packet.getAddress();

                    if (receivingBuzzes && message.equalsIgnoreCase("buzz")) {
                        receivingBuzzes = false;
                        ClientThread winner = findClientByAddress(address);
                        if (winner != null) {
                            winner.setCanAnswer(true);
                            winner.sendMessage("ACK — You may answer!");
                            winner.sendMessage("Time 10");
                        } else {
                            System.out.println("⚠ UDP client match not found.");
                        }
                    } else {
                        ClientThread late = findClientByAddress(address);
                        if (late != null) {
                            late.sendMessage("NAK — Too late!");
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("UDP error: " + e.getMessage());
            }
        }

        private ClientThread findClientByAddress(InetAddress address) {
            for (ClientThread client : clients) {
                if (client.getSocket().getInetAddress().equals(address)) {
                    return client;
                }
            }
            return null;
        }
    }
}
