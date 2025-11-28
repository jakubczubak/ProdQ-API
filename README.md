# ProdQ - Manufacturing Execution System

## Table of Contents

1. [General Information](#general-information)
2. [Technologies Used](#technologies-used)
3. [Key Features](#key-features)
4. [Screenshots](#screenshots)
5. [Configuration](#configuration)
6. [Project Status](#project-status)
7. [Contributing](#contributing)
8. [License](#license)
9. [Contact](#contact)

## General Information

ProdQ is a comprehensive Manufacturing Execution System (MES) designed to enhance CNC machining processes within your production facility. Powered by a React frontend and Spring Boot backend, it adeptly oversees your organization's inventory of tools, materials, and production queue. Offering advanced management functionalities, it includes distinctive features for project cost estimation, supplier performance tracking, and MRP (Material Requirements Planning). The overarching objective of ProdQ is to furnish your CNC machining facility with a dependable tool for monitoring stock levels, managing production workflows, and ensuring seamless operational efficiency.

## Technologies Used

ProdQ leverages a combination of advanced technologies to deliver its comprehensive functionality:

- **Frontend**: React 18 with Material-UI, TanStack Query, i18next
- **Backend**: Spring Boot 3, Java 21
- **Database**: H2 (development) / PostgreSQL (production)
- **Authentication**: JWT (JSON Web Tokens)
- **Containerization**: Docker with ARM64 support (Raspberry Pi compatible)

## Key Features

ProdQ offers a rich set of features tailored to meet the demands of effective inventory and production management in CNC machining facilities:

- **JWT-Based Authentication**: Utilizes JWT for secure and efficient user authentication and authorization.
- **Material Management**: Create material categories, associate specific items with precise dimensions and price attributes, manage quantities, and set minimum stock levels.
- **Tool Management**: Similar capabilities for tools, facilitating efficient inventory control.
- **Production Queue**: Drag-and-drop interface for managing production jobs with material reservations.
- **Order Management**: Create and track orders with full lifecycle management including delivery tracking, invoice reconciliation, and quality ratings.
- **Supplier Performance**: Track and analyze supplier KPIs including delivery times, quality ratings, and pricing accuracy.
- **MRP Module**: Material Requirements Planning with automatic shortage detection and reorder suggestions.
- **Multi-language Support**: Full Polish and English localization.
- **Real-time Notifications**: System-wide notification system for important events.

## Screenshots

Screenshots available in `./src/main/resources/static/screenshots/`

## Configuration

To run ProdQ, follow these steps using Docker Compose:

1. Make sure you have Docker and Docker Compose installed on your system.
2. Navigate to the project's root directory where the `docker-compose.yml` file is located.
3. Open a terminal or command prompt in the project directory.
4. Start the application by running the following command:

   ```sh
   docker-compose --env-file local.env up -d
   ```

5. Access the application by visiting `http://localhost:3000` in your web browser.
6. Log in using the default admin credentials:

   - **Username**: root@gmail.com
   - **Password**: root

7. You can now explore the various features of ProdQ.
8. To stop the application, run the following command:

   ```sh
   docker-compose down
   ```

## Project Status

The project is actively maintained and under continuous development.

## Contributing

Contributions are welcome! Feel free to fork the repository and submit pull requests.

## License

This application is distributed under the MIT License. Feel free to use, modify, and distribute it for any purpose.

## Contact

For any inquiries or further discussions related to the project, please reach out via GitHub.
