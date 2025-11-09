# Java SDK для доступа к OpenWeather API

## Обзор

Этот проект представляет собой SDK на языке **Java** для упрощённого доступа к [OpenWeather API](https://openweathermap.org/current).
SDK позволяет разработчикам получать актуальные данные о погоде по названию населённого пункта, не вникая в детали построения HTTP‑запросов и разбора ответа.
Библиотека поддерживает два режима работы — **on‑demand** и **polling**, реализует кэширование с ограничением по числу городов и времени актуальности, обеспечивает потокобезопасность и выдаёт понятные исключения при ошибках.

> Ниже приведён подробный разбор формата ответа, режимов работы и ограничений кэша, а также англоязычная версия руководства.

## Особенности

* Внешний контракт ограничен фасадом `WeatherSDK`, перечислением `WeatherMode` и исключением `WeatherSDKException`.  Результаты всегда возвращаются в формате `org.json.JSONObject`. Остальные пакеты рассматриваются как внутренняя реализация и могут изменяться без предварительного уведомления.
* Используется **единичный объект** на каждый API‑ключ (паттерн **Singleton**).  Создание второго объекта с тем же ключом невозможно — возвращается уже существующий экземпляр.  Можно **удалить объект**, освободив ресурсы.
* Поддерживается два режима работы: `POLLING, ON_DEMAND`.
* Кэш на основе `LinkedHashMap` с режимом LRU (размер ограничен 10 городами).  Данные хранятся не более 10 минут; при превышении размера самый «старый» элемент автоматически удаляется.
* Внутри используется паттерн **Builder** для настройки сервиса. Это позволяет легко расширять данный SDK в дальнейшем.
* Прозрачная обработка ошибок: если запрос не удался или город не найден, метод бросает проверяемое исключение `WeatherSDKException` с описанием причины.
* Чтение и разбор JSON осуществляются с помощью библиотеки `org.json`.
* Архитектура разделена на слои (клиент, кэш, сервис), что упрощает поддержку.
* <u>Дополнительно</u> реализована поддержка **асинхронных** запросов через `CompletableFuture` для обоих режимов. 

## Режимы работы



| Режим | Когда выбирать | Как работает                                                                                                                                                                                                             |
| --- | --- |--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **ON_DEMAND** | Разовые или редкие запросы по городам. | При обращении пользователя SDK проверяет кэш. Если данные свежие (младше 10 минут), возвращает их без запроса к OpenWeather. Если данные устарели или отсутствуют, выполняет сетевой запрос и сохраняет результат в кэш. |
| **POLLING** | Высокая частота обращений к тем же городам, критичная скорость ответа. | Внутренний планировщик (`ScheduledExecutorService`) обновляет кэш каждые 10 минут для всех городов, уже добавленных в кэш. Клиентские вызовы `getCurrentWeatherJson` получают актуальные данные без сетевой задержки.    |

Режим передаётся при инициализации: `WeatherSDK.getInstance(apiKey, WeatherMode.POLLING)`.  Переключение режима требует уничтожения текущего экземпляра через `WeatherSDK.destroyInstance(apiKey)` и повторного создания с нужным значением.

## Ограничения кэша

- **Максимум 10 городов.** Кэш реализован как LRU-структура, автоматически удаляющая самый старый город при переполнении.
- **Актуальность 10 минут.** При запросе SDK проверяет отметку времени и, если прошло более 10 минут, выполняет обновление.
- **Сброс при уничтожении экземпляра.** Вызов `WeatherSDK.destroyInstance(apiKey)` очищает кэш и останавливает фоновые задачи polling-режима.

## Формат JSON-ответа

SDK возвращает готовый `JSONObject` со следующей структурой:

```json
{
  "weather": {
    "main": "Clouds",
    "description": "scattered clouds"
  },
  "temperature": {
    "temp": 269.6,
    "feels_like": 267.57
  },
  "visibility": 10000,
  "wind": {
    "speed": 1.38
  },
  "datetime": 1675744800,
  "sys": {
    "sunrise": 1675751262,
    "sunset": 1675787560
  },
  "timezone": 3600,
  "name": "Zocca"
}
```

Каждое поле формируется внутри SDK на основе ответа OpenWeather. Пример использования приведён в разделе «Пример работы с JSON».

## Установка

SDK оформлен как Maven‑проект.  Для сборки необходимо:

1. Установить JDK 11 или новее.
2. Склонировать репозиторий или загрузить исходный код.
3. Выполнить команду:

```bash
mvn package -DskipTests
```

В результате будет сформирован файл `target/weather-sdk-1.0.0.jar`, который можно подключить к своему проекту.  Maven также автоматически подтянет зависимость `org.json`.

## Использование

Ниже приведён минимальный пример использования SDK.  Он демонстрирует создание экземпляра, получение информации о погоде и работу с исключениями.

```java
import com.example.weathersdk.*;
import org.json.JSONObject;

public class WeatherExample {
  public static void main(String[] args) {
    // Ваш API‑ключ, полученный на сайте OpenWeather
    String apiKey = "YOUR_API_KEY";
    // Создаём SDK в режиме on‑demand.  При повторном вызове с тем же ключом
    // будет возвращён уже существующий объект.
    WeatherSDK sdk = WeatherSDK.getInstance(apiKey, WeatherMode.ON_DEMAND);
    try {
      // Запрашиваем погоду для Москвы в формате JSON
      JSONObject json = sdk.getCurrentWeatherJson("Moscow");
      System.out.println(json.toString(2));
    } catch (WeatherSDKException e) {
      // Обработка ошибок: неверный ключ, город не найден, проблемы с сетью и т.п.
      System.err.println("Не удалось получить погоду: " + e.getMessage());
    }
    // При необходимости можно уничтожить экземпляр и освободить ресурсы
    WeatherSDK.destroyInstance(apiKey);
  }
}
```

### Асинхронные вызовы и расширенные сценарии

SDK поддерживает асинхронные операции через метод `getCurrentWeatherJsonAsync`, возвращающий `CompletableFuture<JSONObject>`. Это позволяет без блокировки получить те же данные, что и в синхронном примере выше.

Внутренние компоненты (builder, клиенты, кэш) являются частью реализации и могут меняться.  Прямое использование этих классов не поддерживается, чтобы сохранить минимальный публичный контракт.

## Структура проекта

```
weather-sdk/
 ├── pom.xml                            # файл Maven с зависимостями
 ├── README.md                          # данное руководство
 └── src/main/java/com/example/weathersdk/
     ├── api/
     │   └── WeatherService.java        # общий интерфейс сервиса
     ├── client/
     │   ├── WeatherClient.java         # абстракция HTTP‑клиента
     │   └── OpenWeatherClient.java     # реализация через HttpURLConnection
     ├── cache/
     │   ├── Cache.java                 # абстракция кэша
     │   └── LRUCache.java              # LRU‑кэш с размером по умолчанию
     ├── impl/
     │   └── OpenWeatherService.java    # реализация сервиса с polling и билдером
     ├── model/
     │   ├── Units.java                 # единицы измерения (metric, imperial…)
     │   ├── WeatherRequest.java        # объект запроса (город или координаты)
     │   └── WeatherResponse.java       # модель ответа, разбитая на вложенные классы
     ├── serialization/
     │   └── WeatherJsonSerializer.java # преобразование WeatherResponse в целевой JSON
     ├── WeatherMode.java               # перечисление режимов работы (on‑demand/polling)
     ├── WeatherSDK.java                # упрощённая фасад‑обёртка
     └── WeatherSDKException.java       # тип проверяемого исключения
```

Пакеты, отличные от `com.example.weathersdk` и `com.example.weathersdk.model`, предназначены для внутреннего использования.  Их API может меняться между версиями и не гарантируется стабильным для потребителей SDK.

---

## English quick start

### Overview

This Java SDK simplifies access to the [OpenWeather API](https://openweathermap.org/current). It accepts an API key on initialisation, caches responses for frequently requested cities and exposes a high-level facade `WeatherSDK`.

### Response JSON

The SDK returns a ready-to-use `JSONObject` with the following structure:

```json
{
  "weather": {
    "main": "Clouds",
    "description": "scattered clouds"
  },
  "temperature": {
    "temp": 269.6,
    "feels_like": 267.57
  },
  "visibility": 10000,
  "wind": {
    "speed": 1.38
  },
  "datetime": 1675744800,
  "sys": {
    "sunrise": 1675751262,
    "sunset": 1675787560
  },
  "timezone": 3600,
  "name": "Zocca"
}
```

See the “JSON usage example” section below for a code sample.

### Modes of operation

| Mode | Use when | Behaviour |
| --- | --- | --- |
| **ON_DEMAND** | Requests are infrequent. | The SDK fetches data from OpenWeather only if the cached entry is missing or older than 10 minutes. |
| **POLLING** | You need near zero-latency responses. | A scheduler refreshes cached cities every 5 minutes so that `getCurrentWeatherJson` returns fresh data immediately. |

Switch the mode by passing `WeatherMode.ON_DEMAND` or `WeatherMode.POLLING` to `WeatherSDK.getInstance`.

### Cache limits

* Up to **10 cities** are stored simultaneously (LRU eviction).
* Data older than **10 minutes** is refreshed automatically.
* Destroy the instance via `WeatherSDK.destroyInstance(apiKey)` to clear cached data and stop background jobs.

### JSON usage example

```java
WeatherSDK sdk = WeatherSDK.getInstance("YOUR_API_KEY", WeatherMode.ON_DEMAND);
JSONObject json = sdk.getCurrentWeatherJson("Zocca");

System.out.println(json.toString(2));
WeatherSDK.destroyInstance("YOUR_API_KEY");
```

### Asynchronous usage

Call `getCurrentWeatherJsonAsync` to obtain the same payload wrapped in a `CompletableFuture<JSONObject>` when you cannot block the current thread.