# ShopHub Backend

Spring Boot 3.2 REST API powering the ShopHub e-commerce platform.

## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 21 | Language |
| Spring Boot | 3.2.1 | Framework |
| Spring Security | 6.x | Authentication & Authorization |
| Spring Data JPA | 3.2.x | Data Access |
| H2 | - | Development Database |
| PostgreSQL | - | Production Database |
| JJWT | 0.12.3 | JWT Token Management |
| Stripe | 24.3.0 | Payment Processing |
| Cloudinary | 1.36.0 | Image Hosting |
| MapStruct | 1.5.5 | DTO Mapping |
| Lombok | - | Boilerplate Reduction |
| SpringDoc OpenAPI | 2.3.0 | API Documentation |

## Project Structure

```
src/main/java/com/ecommerce/
├── EcommerceApplication.java        # Application entry point
├── config/
│   ├── CorsConfig.java              # CORS policy configuration
│   ├── SecurityConfig.java          # Spring Security & JWT setup
│   └── SwaggerConfig.java           # OpenAPI/Swagger documentation config
├── controller/
│   ├── AdminController.java         # Admin dashboard & management endpoints
│   ├── AuthController.java          # Register, login, token refresh
│   ├── CartController.java          # Shopping cart CRUD
│   ├── CategoryController.java      # Product category listing
│   ├── OrderController.java         # Order placement & tracking
│   ├── PaymentController.java       # Stripe payment intent creation
│   └── ProductController.java       # Product catalog & search
├── dto/
│   ├── request/                     # Incoming request payloads
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   ├── CartItemRequest.java
│   │   ├── OrderRequest.java
│   │   └── ProductRequest.java
│   └── response/                    # Outgoing response payloads
│       ├── AuthResponse.java
│       ├── ProductResponse.java
│       ├── OrderResponse.java
│       ├── UserResponse.java
│       └── DashboardStatsResponse.java
├── entity/
│   ├── User.java                    # User account (email, password, role)
│   ├── Role.java                    # Enum: ADMIN, CUSTOMER
│   ├── Product.java                 # Product details, pricing, stock
│   ├── Category.java                # Product categories
│   ├── CartItem.java                # User shopping cart items
│   ├── Order.java                   # Customer orders
│   ├── OrderItem.java               # Individual items within an order
│   └── OrderStatus.java             # Enum: PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
├── exception/
│   └── GlobalExceptionHandler.java  # Centralized error handling
├── repository/                      # Spring Data JPA repositories
├── security/
│   ├── JwtTokenProvider.java        # JWT generation & validation
│   ├── JwtAuthFilter.java           # Request authentication filter
│   └── UserDetailsServiceImpl.java  # Custom UserDetailsService
└── service/
    ├── AuthService.java             # Registration & authentication logic
    ├── ProductService.java          # Product CRUD & search
    ├── CartService.java             # Cart add/update/remove
    ├── OrderService.java            # Order creation & status management
    ├── PaymentService.java          # Stripe integration
    ├── CategoryService.java         # Category management
    └── AnalyticsService.java        # Admin dashboard statistics
```

## Prerequisites

- Java 21+
- Maven 3.9+

## Getting Started

### Run in Development Mode

```bash
mvn spring-boot:run
```

This starts the API on `http://localhost:8080` with:
- **H2 in-memory database** (no external DB needed)
- **H2 Console**: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:ecommerce_dev`
  - Username: `sa` / Password: *(empty)*
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Debug-level logging** for `com.ecommerce` and Spring Security

### Run in Production Mode

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

Requires a PostgreSQL database and environment variables (see [Configuration](#configuration)).

### Run with Docker

```bash
docker build -t shophub-backend .
docker run -p 8080:8080 \
  -e JWT_SECRET=your-secret \
  -e DB_HOST=host.docker.internal \
  -e DB_PASSWORD=your-password \
  shophub-backend
```

The Dockerfile uses a multi-stage build with `eclipse-temurin:21` (Alpine).

## API Endpoints

### Authentication (`/api/auth`)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | No | Register a new customer account |
| POST | `/api/auth/login` | No | Login and receive JWT tokens |
| POST | `/api/auth/refresh` | No | Refresh an expired access token |
| GET | `/api/auth/me` | Yes | Get current authenticated user |

### Products (`/api/products`)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/products` | No | List products (paginated) |
| GET | `/api/products/{id}` | No | Get product by ID |
| GET | `/api/products/slug/{slug}` | No | Get product by URL slug |
| GET | `/api/products/search?q=` | No | Full-text search |
| GET | `/api/products/featured` | No | Featured products |

### Categories (`/api/categories`)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/categories` | No | List all categories |

### Cart (`/api/cart`)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/cart` | Yes | Get current user's cart |
| POST | `/api/cart` | Yes | Add item to cart |
| PUT | `/api/cart/{id}` | Yes | Update item quantity |
| DELETE | `/api/cart/{id}` | Yes | Remove item from cart |

### Orders (`/api/orders`)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/orders` | Yes | Place a new order |
| GET | `/api/orders` | Yes | List user's orders |
| GET | `/api/orders/{orderNumber}` | Yes | Get order details |

### Payments (`/api/payments`)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/payments/stripe/create-intent` | Yes | Create a Stripe PaymentIntent |

### Admin (`/api/admin`) - Requires ADMIN role

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/admin/dashboard` | Dashboard analytics & stats |
| GET | `/api/admin/orders` | List all orders |
| PUT | `/api/admin/orders/{id}/status` | Update order status |
| GET | `/api/admin/users` | List all users |

## Authentication Flow

1. **Register** - `POST /api/auth/register` with name, email, password
2. **Login** - `POST /api/auth/login` returns `accessToken` and `refreshToken`
3. **Authorize requests** - Include `Authorization: Bearer <accessToken>` header
4. **Refresh** - When the access token expires (24h), call `POST /api/auth/refresh` with the refresh token (valid for 7 days)

## Configuration

### Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `JWT_SECRET` | Yes (prod) | dev default | Base64-encoded secret (min 256 bits) |
| `DB_HOST` | Yes (prod) | `localhost` | PostgreSQL host |
| `DB_PORT` | No | `5432` | PostgreSQL port |
| `DB_NAME` | No | `ecommerce` | Database name |
| `DB_USERNAME` | No | `postgres` | Database user |
| `DB_PASSWORD` | Yes (prod) | - | Database password |
| `STRIPE_SECRET_KEY` | No | - | Stripe secret API key |
| `STRIPE_PUBLIC_KEY` | No | - | Stripe publishable key |
| `STRIPE_WEBHOOK_SECRET` | No | - | Stripe webhook signing secret |
| `PAYPAL_CLIENT_ID` | No | - | PayPal client ID |
| `PAYPAL_CLIENT_SECRET` | No | - | PayPal client secret |
| `PAYPAL_MODE` | No | `sandbox` | `sandbox` or `live` |
| `CLOUDINARY_CLOUD_NAME` | No | - | Cloudinary cloud name |
| `CLOUDINARY_API_KEY` | No | - | Cloudinary API key |
| `CLOUDINARY_API_SECRET` | No | - | Cloudinary API secret |
| `MAIL_HOST` | No | `smtp.gmail.com` | SMTP server host |
| `MAIL_PORT` | No | `587` | SMTP server port |
| `MAIL_USERNAME` | No | - | SMTP username |
| `MAIL_PASSWORD` | No | - | SMTP app password |

### Spring Profiles

| Profile | Database | SQL Logging | DDL Strategy | Use Case |
|---|---|---|---|---|
| `dev` (default) | H2 in-memory | Enabled (formatted) | `update` | Local development |
| `prod` | PostgreSQL | Disabled | `validate` | Production deployment |

## Running Tests

```bash
mvn test
```

## Deployment

### Railway / Render

1. Push code to GitHub
2. Connect repository to Railway or Render
3. Set environment variables from the table above
4. Deploy using the included `Dockerfile`

### Manual JAR

```bash
mvn clean package -DskipTests
java -jar target/ecommerce-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```
