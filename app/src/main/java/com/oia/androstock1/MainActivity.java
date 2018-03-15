package com.oia.androstock1;

import android.Manifest;
import android.os.Build;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    //Declare variables
    private String[] FilePathStrings;
    private String[] FileNameStrings;
    private File[] listFile;
    File file;

    Button btnUpDirectory, btnSDCard;

    ArrayList<String> pathHistory;
    String lastDirectory;
    int count = 0;

    ArrayList<XYValue> uploadData;

    ListView lvInternalStorage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        lvInternalStorage = (ListView) findViewById(R.id.lvInternalStorage);
        btnUpDirectory = (Button) findViewById(R.id.btnUpDirectory);
        btnSDCard = (Button) findViewById(R.id.btnViewSDCard);
        uploadData = new ArrayList<>();

        checkFilePermissions();

        lvInternalStorage.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                lastDirectory = pathHistory.get(count);
                if(lastDirectory.equals(parent.getItemAtPosition(position))){
                    Log.d(TAG, "lvInternalStorage: Selected a file to upload : " + lastDirectory);
                    readExcelData(lastDirectory);
                }else{
                    count++;
                    pathHistory.add(count,(String) parent.getItemAtPosition(position));
                    checkInternalStorage();
                    Log.d(TAG, "lvInternalStorage: " + pathHistory.get(count));
                }
            }
        });

        /**
         * Button Back Listener
         */
        btnUpDirectory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(count == 0){
                    Log.d(TAG, "btnUpDirectory: You have reached the highest level directory.");
                }else{
                    pathHistory.remove(count);
                    count--;
                    checkInternalStorage();
                    Log.d(TAG, "btnUpDirectory: " + pathHistory.get(count));
                }
            }
        });
    }

    private void readExcelData(String filePath) {
        Log.d(TAG, "readExcelData: Reading Excel File.");
        File inputFile = new File(filePath);
        try{
            InputStream inputStream = new FileInputStream(inputFile);
            XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
            XSSFSheet sheet = workbook.getSheetAt(0);
            int rowsCount = sheet.getPhysicalNumberOfRows();
            FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
            StringBuilder sb = new StringBuilder();
            for (int r = 1; r < rowsCount; r++){
                Row row = sheet.getRow(r);
                int cellsCount = row.getPhysicalNumberOfCells();
                for (int c = 0; c < cellsCount; c++){
                    if (c < 2){
                        Log.e(TAG, "readExcelData: ERROR. Excel file format is incorrect.");
                        toastMessage("ERROR: Excel File Format Is Incorrect.");
                        break;
                    }else{
                        String value = getCellAsString(row, c, formulaEvaluator);
                        String cellInfo = "r:" + r + "; c:" + c + "; v:" + value ;
                        Log.d(TAG, "readExcelData: Data from row: " + cellInfo);
                        sb.append(value + ", ");
                    }
                }
                sb.append(":");
            }
            Log.d(TAG, "readExcelData: STRINGBUILDER: " + sb.toString());
            parseStringBuilder(sb);
        }catch (FileNotFoundException e){
            Log.e(TAG, "readExcelData: FileNotFoundException. " + e.getMessage());
        }catch (IOException e){
            Log.e(TAG, "readExcelData: Error reading inputstream. " + e.getMessage());
        }
    }

    private void parseStringBuilder(StringBuilder sb) {
        Log.d(TAG, "parseStringBuilder: Started parsing.");
        String[] rows = sb.toString().split(":");
        for (int i = 0; i < rows.length; i++){
            String[] columns = rows[i].split(",");
            try{
                double x = Double.parseDouble(columns[0]);
                double y = Double.parseDouble(columns[1]);

                String cellInfo = "(x,y): (" + x + "," + y + ")";
                Log.d(TAG, "ParseStringBuilder: Data from row: " + cellInfo);
                uploadData.add(new XYValue(x,y));
            }catch (NumberFormatException e){
                Log.e(TAG, "parseStringBuilder: NumberFormatException: " + e.getMessage());
            }
        }
        printDataToLog();
    }

    private void printDataToLog() {
        Log.d(TAG, "printDataToLog: Printing data to log...");
        for (int i = 0; i < uploadData.size(); i++){
            double x = uploadData.get(i).getX();
            double y = uploadData.get(i).getY();
            Log.d(TAG, "printDataToLog: (x,y): (" + x + "," + y + ")");
        }

    }

    private String getCellAsString(Row row, int c, FormulaEvaluator formulaEvaluator) {
        String value = "";
        try{
            Cell cell = row.getCell(c);
            CellValue cellValue = formulaEvaluator.evaluate(cell);
            switch (cellValue.getCellType()){
                case Cell.CELL_TYPE_BOOLEAN:
                    value = "" + cellValue.getBooleanValue();
                    break;
                case Cell.CELL_TYPE_NUMERIC:
                    double numericValue = cellValue.getNumberValue();
                    if(HSSFDateUtil.isCellDateFormatted(cell)){
                        double date = cellValue.getNumberValue();
                        SimpleDateFormat formatter =
                                new SimpleDateFormat("dd/MM/yyyy");
                        value = formatter.format(HSSFDateUtil.getJavaDate(date));
                    } else {
                        value = ""+numericValue;
                    }
                    break;
                case Cell.CELL_TYPE_STRING:
                    value = ""+cellValue.getStringValue();
                    break;
                default:
            }
        }catch (NullPointerException e){
            Log.e(TAG,"getCellAsString: NullPointerException: " + e.getMessage());
        }
        return  value;
    }

    private void checkInternalStorage() {
        Log.d(TAG, "checkInternalStorage: Started.");
        try{
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
                toastMessage("No SD card found.");
            }else{
                file = new File(pathHistory.get(count));
                Log.d(TAG,"checkInternalStorage: directory path: " + pathHistory.get(count));
            }
            listFile = file.listFiles();
            FilePathStrings = new String[listFile.length];
            FileNameStrings = new String[listFile.length];
            for (int i =  0; i < listFile.length; i++){
                FilePathStrings[i] = listFile[i].getAbsolutePath();
                FileNameStrings[i] = listFile[i].getName();
            }
            for (int i = 0; i < listFile.length; i++){
                Log.d("Files", "FileName: " + listFile[i].getName());
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, FilePathStrings);
            lvInternalStorage.setAdapter(adapter);
        }catch (NullPointerException e){
            Log.e(TAG, "checkInternalStorage: NULLPOINTEREXCEPTION " + e.getMessage());
        }
    }

    private void checkFilePermissions() {
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP){
            int permissionCheck = checkSelfPermission("Manifest.permission.READ_EXTERNAL_STORAGE");

            permissionCheck += checkSelfPermission("Manifest.permission.WRITE_EXTERNAl_STORAGE");
            if(permissionCheck != 0){
                this.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},1001);
            }
        }else{
            Log.d(TAG, "checkBTPermissions: No need to check permissions. SDK Version > LOLLIPOP.");
        }
    }

    private void toastMessage(String message){
        Toast.makeText(this, "", Toast.LENGTH_SHORT).show();
    }
}
