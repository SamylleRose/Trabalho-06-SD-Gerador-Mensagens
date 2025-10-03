package com.exemplo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    private static final String QUEUE_NAME = "team_queue";

    private Connection connection;
    private Channel channel;
    private ObjectMapper objectMapper;
    private EmbeddingAnalyzer analyzer;
    private AtomicLong messagesProcessed;

    public Main() {
        this.objectMapper = new ObjectMapper();
        this.analyzer = new EmbeddingAnalyzer(
                "futebol_embeddings.txt",
                "futebol_labels.txt",
                "model.h5"
        );
        this.messagesProcessed = new AtomicLong(0);
    }

    public void connectRabbitMQ() throws IOException, TimeoutException {
        String host = System.getenv("RABBITMQ_HOST");
        String user = System.getenv("RABBITMQ_USER");
        String pass = System.getenv("RABBITMQ_PASS");

        if (host == null || user == null || pass == null) {
            throw new IllegalStateException("Variáveis de ambiente do RabbitMQ não definidas");
        }

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setUsername(user);
        factory.setPassword(pass);

        connection = factory.newConnection();
        channel = connection.createChannel();
        channel.queueDeclare(QUEUE_NAME, true, false, false, null);
        channel.basicQos(1);
        System.out.println("Conectado ao RabbitMQ em " + host);
    }

    public void startConsuming() throws IOException {
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            try {
                ImageMessage msg = objectMapper.readValue(delivery.getBody(), ImageMessage.class);
                String predictedLabel = analyzer.analyze(msg.getImageData());

                long count = messagesProcessed.incrementAndGet();
                System.out.printf("[%s] Predicted: %s | Total processadas: %d\n",msg.getFileName(), predictedLabel, count);

                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);

            } catch (Exception e) {
                System.err.println("Erro ao processar mensagem: " + e.getMessage());
                e.printStackTrace();
            }
        };

        channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> {});
    }

    public void close() {
        try { if (channel != null && channel.isOpen()) channel.close(); } catch (Exception ignored) {}
        try { if (connection != null && connection.isOpen()) connection.close(); } catch (Exception ignored) {}
    }

    public static void main(String[] args) {
        Main consumer = new Main();
        Runtime.getRuntime().addShutdownHook(new Thread(consumer::close));

        try {
            consumer.connectRabbitMQ();
            consumer.startConsuming();

            while (true) {
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageMessage {
        @JsonProperty("nomeArquivo") private String fileName;
        @JsonProperty("dadosImagem") private byte[] imageData;

        public String getFileName() { return fileName; }
        public byte[] getImageData() { return imageData; }
    }

    public static class EmbeddingAnalyzer {
        private final List<double[]> embeddingsTrain = new ArrayList<>();
        private final List<String> labelsTrain = new ArrayList<>();
        private final ComputationGraph model;

        public EmbeddingAnalyzer(String embeddingsFile, String labelsFile, String modelFile) {
            try {
                List<String> embLines = Files.readAllLines(Paths.get(embeddingsFile), StandardCharsets.UTF_8);
                List<String> lblLines = Files.readAllLines(Paths.get(labelsFile), StandardCharsets.UTF_8);

                for (String line : embLines) {
                    String[] parts = line.trim().split("\\s+");
                    double[] emb = Arrays.stream(parts).mapToDouble(Double::parseDouble).toArray();
                    embeddingsTrain.add(emb);
                }
                labelsTrain.addAll(lblLines);

                model = KerasModelImport.importKerasModelAndWeights(modelFile, false);
                System.out.println("Modelo MobileNetV2 carregado com sucesso!");

            } catch (Exception e) {
                throw new RuntimeException("Erro ao inicializar EmbeddingAnalyzer", e);
            }
        }

        public String analyze(byte[] imageBytes) {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
                BufferedImage resized = resizeImage(img, 224, 224);

                INDArray input = imageToINDArray(resized);
                INDArray embeddingIND = model.outputSingle(input);
                double[] embedding = embeddingIND.toDoubleVector();

                String bestLabel = "ERRO";
                double bestSim = -1.0;

                for (int i = 0; i < embeddingsTrain.size(); i++) {
                    double sim = cosineSimilarity(embedding, embeddingsTrain.get(i));
                    if (sim > bestSim) {
                        bestSim = sim;
                        bestLabel = labelsTrain.get(i).trim();
                    }
                }

                return bestLabel;

            } catch (Exception e) {
                e.printStackTrace();
                return "ERRO";
            }
        }

        private BufferedImage resizeImage(BufferedImage original, int width, int height) {
            BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(original, 0, 0, width, height, null);
            g.dispose();
            return resized;
        }

        private INDArray imageToINDArray(BufferedImage img) {
            int w = img.getWidth();
            int h = img.getHeight();
            INDArray arr = Nd4j.create(1, h, w, 3);

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;

                    arr.putScalar(0, y, x, 0, r / 255.0);
                    arr.putScalar(0, y, x, 1, g / 255.0);
                    arr.putScalar(0, y, x, 2, b / 255.0);
                }
            }
            return arr;
        }

        private double cosineSimilarity(double[] a, double[] b) {
            double dot = 0, normA = 0, normB = 0;
            for (int i = 0; i < a.length; i++) {
                dot += a[i] * b[i];
                normA += a[i] * a[i];
                normB += b[i] * b[i];
            }
            return dot / (Math.sqrt(normA) * Math.sqrt(normB));
        }
    }
}
