# Marketplace_ecommerce_nosql

## CAHIER DES CHARGES

**Projet NoSQL** — Réaliser une application :

- Composée d'au moins deux services ;
- Utilisant au moins deux bases de données NoSQL ;
- Chaque service a son propre agrégat de données ;
- Un des services a un réplica de données de l'autre service ;
- Le réplica est mis à jour via l'échange d'événements entre les services.

**Deux niveaux d'avancement possibles :**

- Sans programmation des services, mais les bases de données doivent être remplies et interrogeables ;
- Avec programmation des services qui utilisent les bases de données.

**Langage de programmation :** libre.

**Livrables :**

- Un mini rapport au format PDF précisant l'objet de l'application, les structures de données utilisées et les bases NoSQL utilisées (choix justifié) ;
- Un dépôt Git contenant l'application ;
- Dépôt du projet sur Moodle.

**Date limite de remise : 15 novembre.**

Le projet est divisé en deux parties :

- **PARTIE 1 : préparation du projet**
- **PARTIE 2 : projet NoSQL**

---

## Avancement de la partie 1

### Étape 1 : Initier le projet

- [x] Créer une équipe de 2 à 4 personnes
- [x] Réfléchir à une application composée de plusieurs services
- [x] Créer un dépôt Git
- [x] Inviter le professeur dans le dépôt (charroux)
- [x] Commencer à rédiger un descriptif du projet (README ou Quarto)

### Étape 2 : Définir les services

- [x] Définir les services
- [x] Définir les commandes associées (demandes de traitement) en JSON

### Étape 3 : Définir les agrégats des services

- [x] Définir les agrégats en JSON

### Étape 4 et 5: Découpler les services

- [x] Définir les événements entre services
- [x] Définir en JSON l'agrégat répliqué (réplication de tout ou partie des données du service émetteur)
- [x] Justifier la réplication : autonomie des services (et disponibilité)
- [x] Décrire chaque événement par :
  - [x] le service émetteur
  - [x] le service récepteur
  - [x] les données de l'agrégat modifiées
  - [x] la nouvelle valeur de la donnée
- [x] Définir au moins un événement qui crée le réplica
- [x] Définir au moins un événement qui met à jour le réplica

### Étape 6 : Projection des données vers de nouveaux services

- [x] Transformer les données du réplica pour les préparer à un nouveau service
- [x] Alimenter le nouveau service par des événements
- [x] Définir l'agrégat de la projection en JSON
- [x] Définir la requête qui interroge la projection

---

## Avancement de la partie 2

### Niveau 1 : bases remplies et interrogeables (sans programmation)

- [x] Choisir et justifier les deux bases NoSQL (Redis et MongoDB)
- [x] Redis : agrégat Stock, files d'événements, persistance AOF
- [ ] MongoDB : agrégat Commande, réplica Livraison, projection order-view
- [ ] Remplir les deux bases en ligne de commande
- [ ] Écrire et tester les requêtes d'interrogation
- [ ] Mettre à jour le réplica par l'échange d'événements

### Niveau 2 : services programmés (Java Spring Boot)

- [ ] orderService (amorcé)
- [ ] event-router
- [ ] stockService
- [ ] deliveryService
- [ ] orderQueryService

---

## Livrables

- [ ] Rapport PDF : objet de l'application, structures de données, bases NoSQL et justification
- [x] Dépôt Git
- [ ] Dépôt sur Moodle (avant le 15 novembre)
