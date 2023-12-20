# INFRABOX - Comprehensive CNC Inventory Management

## Table of Contents

1. [General Info](#general-info)
2. [Technologies Used](#technologies-used)
3. [Key Features](#key-features)
4. [Screenshots](#screenshots)
5. [Configuration](#configuration)
6. [Project Status](#project-status)

## General Info

INFRABOX is a robust CNC inventory management solution tailored for CNC machining facilities. This application, featuring a React-based frontend and a robust Spring Boot backend, effectively manages your organization's inventory of tools and materials. It offers fundamental CRUD (Create, Read, Update, Delete) capabilities for both materials and tools. Additionally, it provides a unique feature to calculate project costs, incorporating material expenses. The primary goal of this project is to provide your CNC machining facility with a reliable solution to monitor stock levels and respond swiftly to any shortages, ensuring the smooth operation of your organization.

## Technologies Used

INFRABOX leverages a combination of advanced technologies to deliver its comprehensive functionality:

- **Frontend (React)**: Developed using React, a popular JavaScript library for building user interfaces.
- **Backend (Spring Boot)**: Powered by Spring Boot, a Java-based framework for creating robust and scalable applications.
- **Java** - Version 17
- **MySQL** - Version 5.7
- **JavaScript** - ECMAScript 6
- **HTML** - Version 5
- **CSS** - Version 3

## Key Features

INFRABOX offers a rich set of features tailored to meet the demands of effective inventory management in CNC machining facilities:

- **JWT-Based Authentication**: Utilizes JWT (JSON Web Tokens) for secure and efficient user authentication and authorization.
- **Login/Logout**: Users can securely log in and out of the system, ensuring data privacy.
- **Admin Account Creation**: Admins have the privilege to create new user accounts, and there's no user self-registration option.
- **Material Management**: Create material categories, associate specific items with precise dimensions and price attributes, manage quantities, and set minimum stock levels.
- **Tool Management**: Similar capabilities for tools, facilitating efficient inventory control.
- **CNC Project Calculations**: Provides a section for detailed CNC project calculations, incorporating parameters such as operating costs (including labor, energy, machine leasing, and depreciation), material costs, tool costs, and CNC machining duration.
- **Orders Management**: The "orders" section allows for creating orders for missing materials and tools. It automatically generates lists of missing items, permits monitoring of order statuses, and streamlines the procurement process.
- **Recycling**: The "Recycling" tab enables you to monitor the recycling of production waste materials, contributing to sustainability and environmental responsibility.
- **Contact Network**: The "Contact Network" section contains a network of contact information for partner companies involved in your CNC machining processes.

## Screenshots

(Please include relevant screenshots of your application here to provide a visual reference for users. You can include images directly within your README.)

## Configuration

To run INFRABOX, follow the steps below using Docker Compose:

1. Ensure that you have Docker and Docker Compose installed on your system.

2. Navigate to the project's root directory where the `docker-compose.yml` fileImage is located.

3. Open a terminal or command prompt in the project directory.

4. Start the application by running the following command:

   docker-compose up -d

This command will launch all containers specified in the `docker-compose.yml` fileImage. The `-d` flag means the containers will run in the background.

5. Once the startup process is complete, the application will be accessible at the appropriate URL, which will be displayed in the console. Typically, this will be `http://localhost` or `http://localhost:port`, where `port` is the port number if defined in the `docker-compose.yml` fileImage.

## Project Status

INFRABOX is an ongoing project with a strong commitment to continuous improvement and feature expansion. I am dedicated to enhancing the user experience and adding new functionalities that further streamline inventory management and project cost estimation in CNC machining facilities. For the latest updates and contributions, please refer to the project's repository.

If you have any questions or need further assistance regarding INFRABOX, please feel free to reach out. I am committed to delivering an effective and user-friendly inventory management solution for your CNC machining facility.
