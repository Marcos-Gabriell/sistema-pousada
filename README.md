# 🏨 Sistema de Gestão para Pousada (Pousada do Brejo)

Sistema completo de gestão interna para pousadas, cobrindo o fluxo **operacional, administrativo e financeiro**, com foco em **segurança**, **regras de negócio**, **auditoria**, **dashboards** e **relatórios em PDF**.

Projeto desenvolvido com arquitetura bem definida, validações robustas e interface moderna (Dark Mode / Light Mode).

---

## ✨ Principais Destaques

- 🔐 Segurança com Spring Security + JWT
- 👥 Controle de acesso por perfis (ADMIN / GERENTE)
- 🏠 Gestão de quartos com validações anti-conflito
- 📅 Reservas e hospedagens com validação de datas
- 💰 Financeiro integrado às hospedagens
- 📊 Dashboard com indicadores e gráficos
- 📄 Relatórios e comprovantes em PDF
- 🔔 Notificações em tempo real (WebSocket)
- 🎨 Interface responsiva com Dark e Light Mode

---

## 🧱 Tecnologias Utilizadas

### Back-end
- Java 11
- Spring Boot
- Spring Data JPA
- Spring Security + JWT
- Bean Validation
- WebSocket
- Thymeleaf (PDF)
- PostgreSQL
- Docker / Docker Compose

### Front-end
- Angular 19
- TypeScript
- RxJS
- Tailwind CSS v4
- jsPDF

---

## 🧩 Módulos e Funcionalidades

### 👥 Usuários e Segurança
- Autenticação e autorização via JWT
- Controle de acesso por roles (ADMIN / GERENTE)
- ADMIN com controle total do sistema
- GERENTE atua apenas no fluxo operacional
- Alteração de senha
- Gerenciamento de perfil
- Auditoria de ações sensíveis
- Limite de edições para ações críticas

### 🏠 Gestão de Quartos
- Cadastro, edição e exclusão
- Status: disponível, ocupado e manutenção
- Validação contra duplicidade
- Bloqueio de múltiplas hospedagens no mesmo período

### 📅 Reservas e Hospedagens
- CRUD de reservas
- Confirmação gera hospedagem automaticamente
- Validação de datas e disponibilidade
- Um quarto não pode ter duas reservas ou hospedagens no mesmo dia

### 💰 Módulo Financeiro
- Entrada automática ao criar hospedagem
- Controle de entradas, saídas e saldo
- Código financeiro por hospedagem
- Auditoria de alterações
- Rastreio completo das movimentações

### 📊 Dashboard Inteligente
- Saldo geral
- Hospedagens ativas
- Quartos ocupados x disponíveis
- Reservas pendentes
- Usuários ativos
- Gráficos de entradas x saídas
- Gráficos de ocupação
- Taxa de ocupação
- Filtros por período

### 📄 Relatórios e Comprovantes (PDF)
- Relatório geral
- Relatórios financeiros
- Relatórios de hospedagens
- Relatórios de reservas
- Relatórios de quartos
- Comprovantes de reserva, hospedagem e financeiro

### 🔔 Notificações em Tempo Real
- Criação de usuários
- Criação, edição e exclusão de hospedagens
- Confirmação de hospedagens
- Alteração de senha

---

## 🎨 Interface e Experiência
- Layout moderno e intuitivo
- Totalmente responsivo
- Dark Mode e Light Mode
- Sidebar expansível no desktop

---

## ⚙️ Como Rodar o Projeto Localmente

### ✅ Pré-requisitos
- Java 11+
- Node.js 18+
- Docker + Docker Compose
- Angular CLI

```bash
npm install -g @angular/cli
```

---

## 🐳 Subindo com Docker (Recomendado)

```bash
git clone https://github.com/Marcos-Gabriell/sistema-pousada/tree/main/api-pousada
cd api-pousada
```

```bash
docker compose up -d
```

---

## 🔧 Back-end (Spring Boot)

### Variáveis de ambiente (`.env`)
```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=pousada
DB_USER=postgres
DB_PASS=postgres
JWT_SECRET=troque_essa_chave
```

### Rodar a API

**Linux / Mac**
```bash
cd backend
./mvnw spring-boot:run
```

**Windows**
```bash
cd backend
mvnw.cmd spring-boot:run
```

```text
http://localhost:8080
```

---

## 🖥️ Front-end (Angular)

```bash
cd frontend
npm install
ng serve -o
```

```text
http://localhost:4200
```

---

## 🗺️ Roadmap

- Melhorias de UX e performance
- Evolução dos relatórios e dashboards
- Mais rastreabilidade e auditoria
- Integração com pagamentos
- Reservas online

---

## 👤 Autor

Desenvolvido por **Marcos**  
GitHub: https://github.com/Marcos-Gabriell

---

## 🎥 Demonstração no YouTube

### Link do vídeo
📺 https://youtu.be/cxnlDaoNQe8




