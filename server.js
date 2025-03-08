const express = require('express');
const bodyParser = require('body-parser');
const cors = require('cors');

const app = express();
const PORT = 3000;

// Middleware
app.use(cors()); // Разрешаем CORS
app.use(bodyParser.json()); // Для парсинга JSON-данных

// Обработка POST-запроса на '/api/device-data'
app.post('/api/device-data', (req, res) => {
    console.log('Полученные данные устройства:', req.body);
    res.status(200).send('Данные успешно получены');
});

// Запуск сервера
app.listen(PORT, () => {
    console.log(`Сервер запущен на http://localhost:${PORT}`); // Используем обратные кавычки для интерполяции
});
