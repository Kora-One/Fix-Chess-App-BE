# ♟️ FixChess AI Analyzer

> **A Core Module of the [KORA ONE](https://github.com/your-repo/kora-one) Ecosystem.**

FixChess is an enterprise-grade chess analysis platform that combines the power of **Google Gemini AI** with high-performance **Spring Boot** architecture. It provides players with deep game insights, character-based roasts, and dynamic player identity cards.

As a specialized component of **KORA ONE**—a centralized multi-tool interface—FixChess leverages unified data streams to provide a seamless, intelligent user experience.

[![Spring Boot](https://img.shields.io/badge/Spring--Boot-4.x-green?style=for-the-badge&logo=springboot)](https://spring.io/projects/spring-boot)
[![Azure](https://img.shields.io/badge/Azure-Cloud-blue?style=for-the-badge&logo=microsoftazure)](https://azure.microsoft.com/)
[![Redis](https://img.shields.io/badge/Redis-Upstash-orange?style=for-the-badge&logo=redis)](https://upstash.com/)

---

## 🚀 Key Features

* **AI Game Roasts:** Get roasted by Gemini 1.5 Flash based on your blunders and accuracy.
* **Dynamic Player Identity:** Analyzes your last 20 games to determine your "Chess Persona" (e.g., Aggressive Attacker vs. Solid Defender).
* **Cost-Efficient Caching:** Integrated with **Upstash Redis** to cache AI responses, reducing API latency by 80%.
* **Fault-Tolerant Architecture:** Custom Circuit Breaker logic allows the app to bypass Redis if the service is down.
* **Secure Infrastructure:** Powered by **Azure Key Vault** for zero-leakage secret management.

---
## 📡 API Endpoints

| Method | Endpoint                   | Description                    |
|:-------|:---------------------------|:-------------------------------|
| `GET`  | `/api/analyze/{username}`  | Generates a game roast         |
| `GET`  | `/api/identity/{username}` | Generates player identity card |
| `GET`  | `/api/stats/{username}`    | Fetches raw Chess.com stats    |