package com.exemplo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

class Main {
    private static final String EXCHANGE_NAME = "image_analysis_exchange";
    private static final String FACES_DIR = "/app/base-rosto/";
    private static final String FOOTBALL_DIR = "/app/base-brasao/";

    private Connection connection;
    private Channel channel;
    private ObjectMapper objectMapper;
    private List<Path> faceImages;
    private List<Path> footballImages;
    private Random random;
    private AtomicLong messagesSent;

    public Main() {
        this.objectMapper = new ObjectMapper();
        this.faceImages = new ArrayList<>();
        this.footballImages = new ArrayList<>();
        this.random = new Random();
        this.messagesSent = new AtomicLong(0);
    }

    public void connectRabbitMQ() throws IOException, TimeoutException {
        String host = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
        String user = System.getenv().getOrDefault("RABBITMQ_USER", "guest");
        String pass = System.getenv().getOrDefault("RABBITMQ_PASS", "guest");

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setUsername(user);
        factory.setPassword(pass);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(5000);

        connection = factory.newConnection();
        channel = connection.createChannel();
        channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.TOPIC, true);

        // Queues separadas
        channel.queueDeclare("face_queue", true, false, false, null);
        channel.queueBind("face_queue", EXCHANGE_NAME, "face");

        channel.queueDeclare("team_queue", true, false, false, null);
        channel.queueBind("team_queue", EXCHANGE_NAME, "team");

        System.out.println("Conectado ao RabbitMQ em: " + host);
    }

    public void loadImages() throws IOException {
        loadDirImagesRecursive(FACES_DIR, faceImages, Arrays.asList(".jpg", ".png"));
        loadDirImagesRecursive(FOOTBALL_DIR, footballImages, Arrays.asList(".jpg", ".png"));

        if (faceImages.isEmpty() && footballImages.isEmpty()) {
            throw new IOException("Nenhuma imagem encontrada nos diretórios.");
        }

        System.out.println("✅ Carregadas " + faceImages.size() + " imagens de rostos");
        System.out.println("✅ Carregadas " + footballImages.size() + " imagens de futebol");
    }

    private void loadDirImagesRecursive(String dir, List<Path> list, List<String> exts) throws IOException {
        Path path = Paths.get(dir);
        if (Files.exists(path) && Files.isDirectory(path)) {
            Files.walk(path)
                 .filter(Files::isRegularFile)
                 .filter(p -> exts.stream().anyMatch(ext -> p.getFileName().toString().toLowerCase().endsWith(ext)))
                 .forEach(list::add);
        }
    }

    public void startSending() {
    System.out.println("Iniciando envio de mensagens a cada 5 segundos...");
    while (true) {
        try {
            if (!faceImages.isEmpty()) {
                MensagemImagem faceMsg = createMessage(faceImages, "face");
                channel.basicPublish(EXCHANGE_NAME, "face",
                        MessageProperties.PERSISTENT_TEXT_PLAIN,
                        objectMapper.writeValueAsBytes(faceMsg));
                 System.out.println("-> Mensagem de rosto enviada.");
            }

            if (!footballImages.isEmpty()) {
                MensagemImagem teamMsg = createMessage(footballImages, "team");
                channel.basicPublish(EXCHANGE_NAME, "team",
                        MessageProperties.PERSISTENT_TEXT_PLAIN,
                        objectMapper.writeValueAsBytes(teamMsg));
                System.out.println("-> Mensagem de brasão enviada.");
            }
            
            
            Thread.sleep(5000); 

        } catch (InterruptedException e) {
            System.err.println("Thread de envio interrompida.");
            Thread.currentThread().interrupt(); // Restaura o status de interrupção
        } catch (Exception e) {
            System.err.println("Erro ao enviar mensagem: " + e.getMessage());
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
    }
}

    private void printStatus(MensagemImagem msg) {
        long count = messagesSent.incrementAndGet();
        if (count % 10 == 0) {
            double tamanhoKB = msg.getImageData().length / 1024.0;
            System.out.printf("Mensagens enviadas: %d - %s (%.1fKB)\n", count, msg.getFileName(), tamanhoKB);
        }
    }

    private MensagemImagem createMessage(List<Path> images, String type) {
        MensagemImagem msg = new MensagemImagem();
        msg.setId(UUID.randomUUID().toString());
        msg.setType(type);
        msg.setTimestamp(System.currentTimeMillis());

        Path imgPath = images.get(random.nextInt(images.size()));
        msg.setFileName(imgPath.toAbsolutePath().toString());

        try {
            msg.setImageData(Files.readAllBytes(imgPath));
        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler imagem: " + imgPath, e);
        }

        return msg;
    }

    public void close() {
        try { if (channel != null && channel.isOpen()) channel.close(); } catch (Exception ignored) {}
        try { if (connection != null && connection.isOpen()) connection.close(); } catch (Exception ignored) {}
    }

    public static void main(String[] args) {
        Main sender = new Main();
        Runtime.getRuntime().addShutdownHook(new Thread(sender::close));

        try {
            sender.connectRabbitMQ();
            sender.loadImages();
            sender.startSending();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class MensagemImagem {
        @JsonProperty("id") private String id;
        @JsonProperty("tipo") private String type;
        @JsonProperty("nomeArquivo") private String fileName;
        @JsonProperty("timestamp") private long timestamp;
        @JsonProperty("dadosImagem") private byte[] imageData;

        public MensagemImagem() {}
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public byte[] getImageData() { return imageData; }
        public void setImageData(byte[] imageData) { this.imageData = imageData; }
    }
}
