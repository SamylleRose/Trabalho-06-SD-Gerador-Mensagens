package com.exemplo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    private static final String QUEUE_NAME = "face_queue";

    private Connection connection;
    private Channel channel;
    private ObjectMapper objectMapper;
    private SentimentAnalyzer analyzer;
    private AtomicLong messagesProcessed;

    public Main() {
        this.objectMapper = new ObjectMapper();
        this.analyzer = new SentimentAnalyzer();
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
                SentimentAnalyzer.Result result = analyzer.analyze(msg.getImageData());

                long count = messagesProcessed.incrementAndGet();
                System.out.printf("[%s] Sentimento: %s | Confiança: %.2f%% | Total processadas: %d\n",
                        msg.getFileName(), result.getSentiment(), result.getConfidence() * 100, count);

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

    public static class SentimentAnalyzer {
        private final MultiLayerNetwork model;

        public SentimentAnalyzer() {
            try {
                model = KerasModelImport.importKerasSequentialModelAndWeights("model.h5");
                System.out.println("Modelo Keras carregado com sucesso!");
            } catch (Exception e) {
                throw new RuntimeException("Erro ao carregar modelo Keras", e);
            }
        }

        public Result analyze(byte[] imageBytes) {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
                BufferedImage resized = new BufferedImage(48, 48, BufferedImage.TYPE_BYTE_GRAY);
                resized.getGraphics().drawImage(img, 0, 0, 48, 48, null);

                INDArray input = Nd4j.create(1, 48, 48, 1);
                for (int y = 0; y < 48; y++) {
                    for (int x = 0; x < 48; x++) {
                        double pixel = resized.getRaster().getSample(x, y, 0) / 255.0;
                        input.putScalar(new int[]{0, y, x, 0}, pixel);
                    }
                }

                INDArray output = model.output(input);
                int predictedClass = Nd4j.argMax(output, 1).getInt(0);
                String[] classes = {"feliz", "triste"};
                String predictedLabel = classes[predictedClass];
                double confidence = output.getDouble(0, predictedClass);

                return new Result(predictedLabel, confidence);
            } catch (Exception e) {
                e.printStackTrace();
                return new Result("ERRO", 0.0);
            }
        }


        public static class Result {
            private final String sentiment;
            private final double confidence;

            public Result(String sentiment, double confidence) {
                this.sentiment = sentiment;
                this.confidence = confidence;
            }

            public String getSentiment() { return sentiment; }
            public double getConfidence() { return confidence; }
        }
    }
}
