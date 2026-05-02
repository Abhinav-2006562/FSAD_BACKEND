# Student Portfolio Backend

Spring Boot + MySQL backend for the React student portfolio frontend.

## Import In Spring Tool Suite

1. Extract this zip.
2. Open Spring Tool Suite.
3. Go to `File > Import > Maven > Existing Maven Projects`.
4. Select the extracted `backend` folder.
5. Finish import.

## MySQL Setup

Start MySQL first. The backend uses:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/student_portfolio?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=root
```

If your MySQL password is different, edit:

```text
src/main/resources/application.properties
```

Tables are created automatically from:

```text
src/main/resources/schema.sql
```

## Run

Right-click:

```text
src/main/java/com/portfolio/backend/StudentPortfolioBackendApplication.java
```

Then choose:

```text
Run As > Spring Boot App
```

Backend runs at:

```text
http://localhost:8080/api
```

## Demo Logins

- Admin: `admin@college.edu` / `admin123`
- Faculty: `sarah@college.edu` / `faculty123`
- Faculty: `mark@college.edu` / `faculty123`
- Student: `arjun@student.edu` / `student123`
- Student: `priya@student.edu` / `student123`
- Student: `ravi@student.edu` / `student123`
