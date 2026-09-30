# Как это устроено / How it works

[Русский](#русский) · [English](#english)

## Русский

### Как часы находят голосовой ввод

На Wear OS 3 голосовой ввод устроен двумя способами, и VoskVoice поддерживает оба:

1. **Экран «Говорите»** (`ui/VoiceInputActivity.kt`). Приложение отправляет интент
   `android.speech.action.RECOGNIZE_SPEECH` и получает текст обратно в `EXTRA_RESULTS`. Так
   работает кнопка микрофона в большинстве приложений. На часах этот интент по умолчанию
   обрабатывает Gboard для Wear (`WearRemoteInputActivity`), который распознаёт речь на серверах
   Google. Когда установлен VoskVoice, система показывает выбор (на Wear это
   `com.google.android.apps.wearable.settings/…ResolverActivity`). Выбор «Всегда» делает
   VoskVoice обработчиком по умолчанию.
2. **Системный распознаватель** (`service/VoskRecognitionService.kt`). Приложения, которые
   показывают свой интерфейс и распознают через `SpeechRecognizer`, обращаются к сервису из
   настройки `Settings.Secure.voice_recognition_service`. На TicWatch Pro 3 она пустая,
   распознавателя в системе нет вообще, поэтому её задаём через ADB.

### Путь звука

```
микрофон 16 кГц, mono, PCM16 (VOICE_RECOGNITION, запасной вариант MIC)
  → поток «dictation-mic»: куски по 100 мс → очередь
  → поток «dictation-<язык>»: модель из ModelCache → Recognizer.acceptWaveForm
  → частичный текст (partial) на экран, итог после паузы
```

Микрофон открывается **до** загрузки модели. Если модель ещё не в памяти, звук копится в
очереди и распознаётся, как только модель готова. Говорить можно сразу, ничего не теряется.

Конец фразы определяет эндпойнтер Kaldi (`Recognizer.setEndpointerDelays`):

| Параметр | Значение | Смысл |
|---|---|---|
| Нет речи | 6 с | Если за это время ничего не сказано, «Не расслышал» |
| Пауза в конце | 1,2 с | Столько тишины после речи завершает фразу |
| Максимум | 30 с | Самая длинная фраза |

Эндпойнтер считает время по звуку, а не по часам, поэтому буферизованный звук оценивается
правильно. Нажатие на микрофон заканчивает фразу раньше: оставшийся звук из очереди
дораспознаётся, и берётся итоговый результат.

Маленькие модели выдают текст строчными буквами без знаков препинания. `TextFormat` делает
первую букву заглавной и исправляет английское «i» на «I».

### Замеры на TicWatch Pro 3 GPS

Snapdragon Wear 4100, 32-битный режим, ~900 МБ ОЗУ, Wear OS 3.5, модель
`vosk-model-small-ru-0.22`.

| Что | Значение |
|---|---|
| Загрузка модели, холодный старт процесса | 5,5–7,8 с |
| Загрузка модели сразу после установки APK | 14–17 с |
| Скорость распознавания | 0,55–0,95 от реального времени (6,2 с звука за 3,3–6,0 с) |
| Память процесса с моделью | ~150–180 МБ |
| Свободно в системе с загруженной моделью | ~250–280 МБ (zram) |
| Открытие экрана «Говорите» | 1,3–1,8 с |

Загрузка упирается в процессор, а не в чтение с диска: при повторной загрузке, когда файлы уже
в кеше, она почти не быстрее. В модели два графа по ~32 МБ в компактных форматах OpenFst
(`olabel_lookahead` и `ngram`), и Kaldi перестраивает их таблицы при загрузке.

### Почему модель держится в памяти

Ждать 5–8 секунд при каждом нажатии на микрофон неудобно. Поэтому `ModelKeeperService`
(foreground-сервис с незаметным уведомлением) не даёт системе завершить процесс, и модель,
загруженная один раз, остаётся в `ModelCache`. Сам сервис микрофон не использует.

Особенность, найденная на часах: сразу после загрузки модели система присылает
`onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)`, хотя свободно ещё ~280 МБ. Если на него реагировать,
режим теряет смысл, поэтому в режиме «держать» модель отдаётся только по
`TRIM_MEMORY_COMPLETE`. Кроме того, `TRIM_MEMORY_UI_HIDDEN` (20) численно больше
`RUNNING_CRITICAL` (15), хотя означает лишь «экран приложения закрыт», поэтому сравнивать уровни
через `>=` нельзя. Правило вынесено в `speech/TrimPolicy.kt` и покрыто тестами.

Без режима «держать» модель выгружается через 5 минут простоя.

### Хранение моделей

Модели лежат во внутренней памяти приложения: `files/models/ru`, `files/models/en`.
`ModelDownloader` скачивает zip и распаковывает его на лету, без временного архива, во временную
папку `<язык>.part`, которая переименовывается только после успешной распаковки. Прерванная
загрузка никогда не выглядит установленной моделью. Пути в архиве проверяются, чтобы файл не мог
записаться за пределы папки (Zip Slip).

`/sdcard/Android/data` не используется: на TicWatch Pro 3 туда не пускают даже ADB.

## English

### How the watch finds voice input

Wear OS 3 has two ways to do voice input, and VoskVoice supports both:

1. **The "Speak now" screen** (`ui/VoiceInputActivity.kt`). An app sends the
   `android.speech.action.RECOGNIZE_SPEECH` intent and gets the text back in `EXTRA_RESULTS`.
   This is what the microphone button does in most apps. By default the intent is handled by
   Gboard for Wear (`WearRemoteInputActivity`), which recognizes speech on Google servers. With
   VoskVoice installed the system shows a chooser (on Wear it is
   `com.google.android.apps.wearable.settings/…ResolverActivity`). Choosing "Always" makes
   VoskVoice the default.
2. **The system recognizer** (`service/VoskRecognitionService.kt`). Apps that show their own UI
   and use `SpeechRecognizer` talk to the service named in
   `Settings.Secure.voice_recognition_service`. On the TicWatch Pro 3 it is empty (the system
   has no recognizer at all), so it is set with ADB.

### Audio path

```
microphone 16 kHz, mono, PCM16 (VOICE_RECOGNITION, falling back to MIC)
  → thread "dictation-mic": 100 ms chunks → queue
  → thread "dictation-<lang>": model from ModelCache → Recognizer.acceptWaveForm
  → partial text on screen, final text after a pause
```

The microphone opens **before** the model is loaded. If the model is not in memory yet, audio is
queued and recognized as soon as the model is ready. You can talk right away and nothing is lost.

The end of a phrase is detected by Kaldi's endpointer (`Recognizer.setEndpointerDelays`):

| Setting | Value | Meaning |
|---|---|---|
| No speech | 6 s | Nothing said by then → "Didn't catch that" |
| Trailing pause | 1.2 s | This much silence after speech ends the phrase |
| Maximum | 30 s | Longest phrase |

The endpointer measures audio time, not wall-clock time, so buffered audio is judged correctly.
Tapping the microphone ends the phrase early: the queued audio is decoded and the final result
is taken.

Small models output lowercase text without punctuation. `TextFormat` capitalizes the first
letter and fixes the English "i" to "I".

### Measurements on a TicWatch Pro 3 GPS

Snapdragon Wear 4100 in 32-bit mode, ~900 MB RAM, Wear OS 3.5, model
`vosk-model-small-ru-0.22`.

| What | Value |
|---|---|
| Model load, cold process start | 5.5–7.8 s |
| Model load right after installing the APK | 14–17 s |
| Recognition speed | 0.55–0.95× real time (6.2 s of audio in 3.3–6.0 s) |
| Process memory with a model | ~150–180 MB |
| Free system memory with the model loaded | ~250–280 MB (zram) |
| Opening the "Speak now" screen | 1.3–1.8 s |

Loading is CPU-bound, not disk-bound: a second load with the files already cached is barely
faster. The model has two ~32 MB graphs in compact OpenFst formats (`olabel_lookahead` and
`ngram`), and Kaldi rebuilds their tables on load.

### Why the model stays in memory

Waiting 5–8 s every time you tap the microphone is not usable. So `ModelKeeperService` (a
foreground service with an unobtrusive notification) keeps the process alive, and a model
loaded once stays in `ModelCache`. The service never uses the microphone.

A quirk found on the watch: right after the model loads, the system sends
`onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)` even though ~280 MB are still available. Honoring it
would make the mode pointless, so in "keep" mode the model is only given up on
`TRIM_MEMORY_COMPLETE`. Also, `TRIM_MEMORY_UI_HIDDEN` (20) is numerically greater than
`RUNNING_CRITICAL` (15) while only meaning "the app's screen was closed", so levels must not be
compared with `>=`. The rule lives in `speech/TrimPolicy.kt` and is covered by tests.

Without "keep" mode the model is unloaded after 5 minutes of inactivity.

### Model storage

Models live in the app's internal storage: `files/models/ru`, `files/models/en`.
`ModelDownloader` downloads the zip and unpacks it on the fly, without a temporary archive, into a
temporary `<lang>.part` folder that is renamed only after a successful unpack. An interrupted
download never looks like an installed model. Archive paths are checked so no file can be
written outside the target folder (Zip Slip).

`/sdcard/Android/data` is not used: on the TicWatch Pro 3 not even ADB is allowed in there.
