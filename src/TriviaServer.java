import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class TriviaServer {
    private static final int TCP_PORT = 1234;
    private static final int UDP_PORT = 1235;
    private static final List<Question> questions = new ArrayList<>();
    private static final List<ClientThread> clients = new ArrayList<>();
    private static final List<ClientThread> buzzOrder = new ArrayList<>();
    private static final ExecutorService pool = Executors.newCachedThreadPool();

    private static int currentQuestionIndex = 0;
    private static boolean receivingBuzzes = true;
    private static boolean hasPrintedWinners = false;
    private static ServerSocket serverSocket;

    private static Timer activeTimer = new Timer();

    public static void main(String[] args) {
        loadQuestions();

        try {
            serverSocket = new ServerSocket(TCP_PORT);
            System.out.println("Trivia Server started on port " + TCP_PORT);

            new UDPBuzzThread().start();

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
                        break;
                    }
                }
            }).start();

            while (clients.size() < 2) {
                Thread.sleep(500);
            }

            System.out.println("Starting Trivia Game!");
            sendNextQuestionToAll();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendNextQuestionToAll() throws IOException {
    	buzzOrder.clear();
        if (currentQuestionIndex >= questions.size()) {
            endGame();
            return;
        }

        receivingBuzzes = true;
        Question q = questions.get(currentQuestionIndex);
        System.out.println("\n❓ " + q.getQuestionNumber() + ": " + q.getQuestionText());

        // Send question and options to all clients
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

     // 🕓 Start 15-second poll timer
        startTimer(15, () -> {
            receivingBuzzes = false;

            if (!buzzOrder.isEmpty()) {
                ClientThread winner = buzzOrder.get(0);
                winner.setCanAnswer(true);
                winner.sendMessage("ACK");
                System.out.println("🎯 Client-" + winner.getClientID() + " buzzed first and may answer.");

                // NAK all other clients
                for (ClientThread client : clients) {
                    if (client != winner) {
                        client.sendMessage("NAK");
                    }
                }

                // Start 10s answer timer
                startTimer(10, () -> {
                    try {
                        clientOutOfTime(winner);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            } else {
                System.out.println("⏱️ No one buzzed in. Skipping to next question.");
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


    private static void endGame() throws IOException {
        if (hasPrintedWinners) return;
        hasPrintedWinners = true;

        System.out.println("\n Game Over. Final Scores:");
        clients.sort((a, b) -> b.getScore() - a.getScore());

        for (ClientThread client : new ArrayList<>(clients)) {
            client.sendMessage("Game Over! Your final score: " + client.getScore());
            System.out.println("Client " + client.getClientID() + ": " + client.getScore());
            //client.close();
            TriviaServer.removeClient(client);
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
                    String message = new String(packet.getData(), 0, packet.getLength());
                    InetAddress address = packet.getAddress();

                    if (message.equalsIgnoreCase("buzz") && receivingBuzzes) {
                        ClientThread client = findClientByAddress(address);
                        if (client != null && !buzzOrder.contains(client)) {
                            buzzOrder.add(client);
                            System.out.println("Buzz received from Client-" + client.getClientID());
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("UDP Thread error");
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


        private ClientThread findClientByAddress(InetAddress address) {
            for (ClientThread client : clients) {
                if (client.getSocket().getInetAddress().equals(address)) {
                    return client;
                }
            }
            return null;
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
        client.close();
    }

    public static int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public static void clientOutOfTime(ClientThread client) throws IOException {
        client.sendMessage("Time expired");
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
}
