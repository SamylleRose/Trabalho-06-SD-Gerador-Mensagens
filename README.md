# Sistema Distribuído para Análise de Imagens com Java e RabbitMQ

Este projeto demonstra a implementação de um sistema distribuído focado em processamento de imagens. Utilizando Java para os serviços, Docker para a containerização e RabbitMQ como message broker, a arquitetura é composta por um publicador de mensagens e dois serviços consumidores com inteligência artificial para análise de conteúdo visual.

## Arquitetura e Componentes

O ecossistema é orquestrado via `docker-compose.yml` e dividido nos seguintes serviços:

- **`rabbitmq`**: Atua como o nó central de comunicação (message broker), utilizando a imagem oficial `rabbitmq:3-management` para incluir uma interface de gerenciamento web.
- **`gerador-mensagens`**: Um serviço em Java responsável por simular uma carga de trabalho, publicando mensagens com imagens em duas categorias distintas (faces e brasões) no broker RabbitMQ.
- **`analise-rosto`**: Serviço consumidor que processa imagens de rostos. Ele utiliza um modelo de IA para realizar análise de sentimento, classificando as imagens recebidas.
- **`identifica-brasao`**: O segundo serviço consumidor, especializado em identificar brasões de times de futebol a partir das imagens recebidas, utilizando seus próprios modelos e embeddings.

## 🛠️ Tecnologias Utilizadas

- **Linguagem**: Java 11
- **Containerização**: Docker e Docker Compose
- **Mensageria**: RabbitMQ
- **Build**: Maven
- **Processamento de Imagem**: Bibliotecas nativas do Java (AWT/BufferedImage) para análise visual.
- **Serialização**: Jackson para manipulação de dados em formato JSON.

## 🚀 Como Executar o Ambiente

Para iniciar o sistema, certifique-se de que o Docker e o Docker Compose estão instalados e em execução na sua máquina.

**1. Construir e Iniciar os Serviços**

Execute o comando a seguir na raiz do projeto para construir as imagens e iniciar todos os containers em modo detached (`-d`):

```bash
docker compose up --build -d
```

**2. Verificar o Status dos Containers**

Para confirmar que todos os serviços estão em execução, use o comando:

```bash
docker compose ps
```

**3. Monitorar os Logs**

Você pode acompanhar os logs de um serviço específico em tempo real. Por exemplo, para ver os logs do gerador de mensagens:

```bash
docker compose logs -f gerador-mensagens
```

**4. Acessar a Interface do RabbitMQ**

A interface de gerenciamento do RabbitMQ estará disponível no seu navegador em:
**URL**: `http://localhost:15672`
**Credenciais**: `admin` / `admin123` (conforme definido em `docker-compose.yml`)

## 🛑 Como Parar o Ambiente

Para parar e remover todos os containers, redes e volumes criados pelo Compose, utilize o comando:

```bash
docker compose down
```

Se preferir parar apenas os containers sem removê-los, você pode executar:

```bash
docker compose stop
```

## 📁 Estrutura do Projeto

O repositório está organizado da seguinte forma para separar as responsabilidades de cada serviço:

```
/
├── docker-compose.yml        # Arquivo principal de orquestração dos serviços
├── gerador-mensagens/        # Código-fonte e Dockerfile do publicador
│   ├── base-rosto/
│   └── base-brasao/
├── analise-rosto/            # Código-fonte, modelo de IA e Dockerfile do consumidor de rostos
│   └── model.h5
├── identifica-brasao/        # Código-fonte, modelos e Dockerfile do consumidor de brasões
│   ├── futebol_embeddings.txt
│   ├── futebol_labels.txt
│   └── model.h5
├── rabbitmq/                 # Configurações pré-definidas para o RabbitMQ
│   └── definitions.json
└── README.md                 # Esta documentação
```
