val host: String by env
val counterId: String by env

POST("$host/counter/$counterId/inc")
