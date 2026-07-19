# Kubernetes Learning Projects

A comprehensive repository for learning Kubernetes from fundamentals to advanced concepts, organized with hands-on projects and structured learning paths.

## 📚 Repository Structure

### **Core Learning Paths**

- **`Test/`** - Daily structured learning modules following a 20-hour beginner roadmap
  - Day-by-day breakdown (Days 01-09) with core concepts, practical examples, and Q&A
  - Kubernetes-Roadmap.md - Complete learning roadmap with 80/20 principle
  - SRE.md - Site Reliability Engineering concepts
  - Task.md - Learning objectives and goals

- **`Kodekloud/`** - Materials from Kodekloud Kubernetes courses
  
- **`Kube-expert/`** - Advanced Kubernetes expert-level materials

- **`Fast-SRE/`** - SRE (Site Reliability Engineering) focused learning

- **`Go-20hr-Crash-Course/`** - Go programming language crash course (companion for understanding containerized applications)

- **`Claude-Learning/`** - DevOps projects including:
  - `devops-lib/` - DevOps utilities and libraries
  - `ringmaster-backend/` - Backend application example
  - `ringmaster-frontend/` - Frontend application example
  - `valmo-devops-lib/` - Additional DevOps libraries

### **Additional Resources**

- **`Repos-Learning/`** - External repository learning resources
  - observability-zero-to-hero - Observability and monitoring concepts

- **`20-hours-plan/`** - Quick reference for the 20-hour learning structure

- **`kubernates/`** - Kubernetes configuration examples and experiments

### **Scripts**

- **`pre-commit-scripts/`** - Pre-commit hooks for validation
- **`post-commit-scripts/`** - Post-commit hooks for automated tasks

## 🎯 Learning Objectives

This repository focuses on core Kubernetes fundamentals using the **80/20 principle** - learning the essential 20% of concepts that deliver 80% of practical value:

### Core Topics Covered
- ✅ Pod basics and Deployments
- ✅ Services and Networking
- ✅ ConfigMaps and Secrets
- ✅ StatefulSets
- ✅ Ingress
- ✅ Namespaces and RBAC basics
- ✅ Persistent Volumes
- ✅ Troubleshooting and Logging
- ✅ Go programming fundamentals (for containerized apps)

## 🚀 Getting Started

### Prerequisites
- Docker installed
- kubectl installed
- Minikube or Kind (for local Kubernetes cluster)
- Basic command line knowledge

### Quick Start

1. **Start with the structured learning path:**
   ```bash
   cd Test/
   # Begin with Day-01.md and progress sequentially
   ```

2. **Follow the Kubernetes Roadmap:**
   ```bash
   cat Test/Kubernetes-Roadmap.md
   ```

3. **Practice with hands-on examples:**
   Each day includes practical projects using free tools (Minikube, Kind, Play with Kubernetes)

4. **Reference DevOps projects:**
   Explore Claude-Learning/ for real-world application examples

## 📖 Study Structure

### Daily Learning Format
Each learning module includes:
- **Core Concepts** - 3-5 key ideas to master
- **Practical Examples** - Hands-on demonstrations
- **Q&A** - 50+ conceptual, scenario-based, and command-based questions with detailed answers
- **Projects** - Beginner-friendly practical exercises
- **Free Resources** - Official docs, tutorials, and tools

### Time Allocation
- Target: 2-4 hours of focused learning per day
- Total: ~20 hours for foundational knowledge
- Flexible pace for deeper understanding

## 🛠️ Tools & Technologies

### Kubernetes Platforms
- **Minikube** - Local Kubernetes cluster
- **Kind** - Kubernetes in Docker
- **Play with Kubernetes** - Free online Kubernetes playground

### DevOps & SRE
- kubectl - Kubernetes command-line tool
- Docker - Container platform
- Git - Version control
- Go - Programming language for cloud-native apps

## 📝 Learning Resources

All resources linked in this repository are:
- ✅ Free or free-tier available
- ✅ Official Kubernetes documentation
- ✅ Community-maintained projects
- ✅ No paywalled content

## 🔗 Related Topics

- **Go Programming** - Essential for writing cloud-native applications
- **Docker** - Container fundamentals prerequisite
- **CI/CD** - Deployment pipelines and automation
- **Observability** - Monitoring, logging, and tracing (observability-zero-to-hero)
- **SRE** - Operations and reliability practices

## 💡 How to Use This Repository

1. **For Beginners:** Start with `Test/Day-01.md` and follow the sequential daily guides
2. **For Reference:** Jump to specific topics using the directory structure
3. **For Practice:** Work through projects in each learning module
4. **For Deep Dives:** Explore course materials in Kodekloud/, Kube-expert/, and Fast-SRE/
5. **For Context:** Review DevOps projects in Claude-Learning/ for real-world applications

## 🎓 Learning Philosophy

This repository follows the **80/20 principle** - focus on essential concepts that provide maximum practical value. Advanced topics (operators, custom resources, service meshes) are intentionally deferred until foundational knowledge is solid.

## 📚 Progress Tracking

- [ ] Day 01 - Pod Basics & Installation
- [ ] Day 02 - Deployments & Replication
- [ ] Day 03 - Services & Networking
- [ ] Day 04 - ConfigMaps & Secrets
- [ ] Day 05 - Persistent Volumes & Storage
- [ ] Day 06 - StatefulSets
- [ ] Day 07 - Ingress & Load Balancing
- [ ] Day 08 - Namespaces & RBAC
- [ ] Day 09 - Troubleshooting & Logging

## 🤝 Contributing

This is a personal learning repository. Feel free to fork and adapt the materials for your own learning journey.

## 📞 Support

For questions about specific topics, refer to:
- Official Kubernetes Documentation: https://kubernetes.io/docs/
- Community Forums: https://discuss.kubernetes.io/
- Stack Overflow: Tag your questions with `kubernetes`

---

**Last Updated:** July 2026  
**Status:** Active Learning Repository
