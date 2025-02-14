import javax.swing.*
import java.awt.*
import java.io.*
import java.nio.charset.Charset
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import javax.swing.filechooser.FileNameExtensionFilter

// Расширенное приложение-конвертер CSV в JSON с использованием Swing
class CSVtoJSONConverterApp : JFrame("CSV в JSON конвертер (расширенный)") {

    private val filePathField = JTextField(30)
    private val delimiterField = JTextField(2)
    private val encodingComboBox = JComboBox(arrayOf("UTF-8", "CP1251"))
    private val columnsField = JTextField(30)
    private val progressLabel = JLabel("Ожидание...")
    private val resultTextArea = JTextArea(20, 50)

    private var selectedCSVFile: File? = null

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(700, 600)
        setLocationRelativeTo(null)

        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // Панель выбора CSV-файла
        val filePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        filePanel.add(JLabel("Выберите CSV файл:"))
        filePathField.isEditable = false
        filePanel.add(filePathField)
        val browseButton = JButton("Обзор")
        browseButton.addActionListener {
            val chooser = JFileChooser()
            chooser.fileFilter = FileNameExtensionFilter("CSV файлы", "csv")
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                selectedCSVFile = chooser.selectedFile
                filePathField.text = selectedCSVFile?.absolutePath ?: ""
            }
        }
        filePanel.add(browseButton)
        panel.add(filePanel)

        // Панель ввода символа-разделителя
        val delimiterPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        delimiterPanel.add(JLabel("Символ-разделитель:"))
        delimiterField.text = ","
        delimiterPanel.add(delimiterField)
        panel.add(delimiterPanel)

        // Панель выбора кодировки
        val encodingPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        encodingPanel.add(JLabel("Кодировка:"))
        encodingPanel.add(encodingComboBox)
        panel.add(encodingPanel)

        // Панель выбора столбцов (через запятую)
        val columnsPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        columnsPanel.add(JLabel("Столбцы для включения (через запятую, пусто для все):"))
        columnsPanel.add(columnsField)
        panel.add(columnsPanel)

        // Кнопка запуска конвертации
        val convertButton = JButton("Конвертировать")
        convertButton.addActionListener {
            startConversion()
        }
        panel.add(convertButton)

        // Информационная метка для отображения прогресса
        panel.add(progressLabel)

        // Текстовая область для отображения результата
        resultTextArea.isEditable = false
        panel.add(JScrollPane(resultTextArea))

        // Кнопка сохранения JSON
        val saveButton = JButton("Сохранить JSON")
        saveButton.addActionListener {
            saveJSONToFile()
        }
        panel.add(saveButton)

        add(panel)
    }

    // Запуск процесса конвертации в отдельном потоке
    private fun startConversion() {
        if (selectedCSVFile == null) {
            JOptionPane.showMessageDialog(this, "Сначала выберите CSV файл.", "Ошибка", JOptionPane.ERROR_MESSAGE)
            return
        }

        val delimiterText = delimiterField.text
        if (delimiterText.isEmpty() || delimiterText.length != 1) {
            JOptionPane.showMessageDialog(this, "Введите корректный символ-разделитель.", "Ошибка", JOptionPane.ERROR_MESSAGE)
            return
        }
        val delimiterChar = delimiterText[0]

        val encoding = encodingComboBox.selectedItem as String

        val columnsText = columnsField.text.trim()
        // Если пользователь указал столбцы, разделяем по запятой
        val includedColumns: Set<String>? = if (columnsText.isNotEmpty()) {
            columnsText.split(",").map { it.trim() }.toSet()
        } else {
            null
        }

        progressLabel.text = "Начало конвертации..."

        // Запуск конвертации в фоне с помощью SwingWorker
        object: SwingWorker<String, Void>() {
            override fun doInBackground(): String {
                try {
                    return convertCSVtoJSON(selectedCSVFile!!, delimiterChar, encoding, includedColumns)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    return "Ошибка: ${ex.message}"
                }
            }

            override fun done() {
                try {
                    val result = get()
                    resultTextArea.text = result
                    progressLabel.text = "Конвертация завершена."
                } catch (ex: Exception) {
                    progressLabel.text = "Ошибка при конвертации."
                    JOptionPane.showMessageDialog(this@CSVtoJSONConverterApp, "Ошибка: ${ex.message}", "Ошибка", JOptionPane.ERROR_MESSAGE)
                }
            }
        }.execute()
    }

    // Функция сохранения результирующего JSON в файл
    private fun saveJSONToFile() {
        if (resultTextArea.text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Нет данных для сохранения.", "Ошибка", JOptionPane.ERROR_MESSAGE)
            return
        }
        val chooser = JFileChooser()
        chooser.dialogTitle = "Сохранить JSON файл"
        chooser.fileSelectionMode = JFileChooser.FILES_ONLY
        chooser.selectedFile = File("result.json")
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                val file = chooser.selectedFile
                file.writeText(resultTextArea.text)
                JOptionPane.showMessageDialog(this, "Файл сохранён: ${file.absolutePath}")
            } catch (ex: Exception) {
                JOptionPane.showMessageDialog(this, "Ошибка при сохранении файла: ${ex.message}", "Ошибка", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    // Функция конвертации CSV в JSON
    private fun convertCSVtoJSON(file: File, delimiter: Char, encoding: String, includedColumns: Set<String>?): String {
        val reader = InputStreamReader(FileInputStream(file), Charset.forName(encoding))
        // Парсим CSV с заголовками, используя заданный разделитель
        val csvParser = CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(delimiter))
        val records = csvParser.records

        // Получаем список столбцов из заголовка
        val headerMap = csvParser.headerMap
        val headers = headerMap.keys

        // Если пользователь указал конкретные столбцы, проверяем их наличие
        if (includedColumns != null) {
            for (col in includedColumns) {
                if (!headers.contains(col)) {
                    throw Exception("Отсутствует необходимый столбец: $col")
                }
            }
        }

        // Формируем список записей (каждая запись – Map<String, String>)
        val dataList = mutableListOf<Map<String, String>>()
        for (record in records) {
            val map = mutableMapOf<String, String>()
            for (header in headers) {
                if (includedColumns == null || includedColumns.contains(header)) {
                    map[header] = record.get(header)
                }
            }
            dataList.add(map)
        }

        // Преобразуем список в JSON с помощью Jackson
        val mapper = jacksonObjectMapper().registerKotlinModule()
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(dataList)
    }
}

fun main() {
    SwingUtilities.invokeLater {
        val app = CSVtoJSONConverterApp()
        app.isVisible = true
    }
}
