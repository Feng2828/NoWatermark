package com.sopro.nowatermark.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.sopro.nowatermark.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** 内置文件夹选择器：不依赖外部文件管理器，便于在 HyperOS 上稳定选目录。 */
public class FolderPickerActivity extends AppCompatActivity {

    public static final String EXTRA_START_PATH = "start_path";
    public static final String RESULT_PATH = "path";

    private File current;
    private TextView pathText;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<File> directories = new ArrayList<File>();
    private File root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_picker);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        root = Environment.getExternalStorageDirectory();
        String start = getIntent().getStringExtra(EXTRA_START_PATH);
        File startFile = (start != null && start.length() > 0) ? new File(start) : root;
        if (!startFile.isDirectory()) startFile = root;
        current = startFile;

        pathText = findViewById(R.id.pathText);
        listView = findViewById(R.id.folderList);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < directories.size()) {
                    current = directories.get(position);
                    refresh();
                }
            }
        });

        findViewById(R.id.upButton).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                File parent = current.getParentFile();
                if (parent != null && parent.canRead()
                        && (parent.equals(root) || parent.getAbsolutePath().startsWith(root.getAbsolutePath()))) {
                    current = parent;
                    refresh();
                } else {
                    Toast.makeText(FolderPickerActivity.this, R.string.picker_path, Toast.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.selectButton).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent data = new Intent();
                data.putExtra(RESULT_PATH, current.getAbsolutePath());
                setResult(RESULT_OK, data);
                finish();
            }
        });

        refresh();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void refresh() {
        pathText.setText(getString(R.string.picker_path, current.getAbsolutePath()));
        directories.clear();
        File[] files = current.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory() && !f.isHidden()) directories.add(f);
            }
        }
        java.util.Collections.sort(directories, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });

        List<String> names = new ArrayList<String>();
        if (directories.isEmpty()) {
            names.add(getString(R.string.picker_no_subdir));
        } else {
            for (File d : directories) names.add(d.getName());
        }
        adapter.clear();
        adapter.addAll(names);
        adapter.notifyDataSetChanged();
    }
}
