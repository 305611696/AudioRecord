package audiorecord.wtt.com.audiorecord;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Button mBtnFileModel;
    private Button mBtnStreamModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //初始化View
        mBtnFileModel = (Button) findViewById(R.id.button1);
        mBtnStreamModel = (Button) findViewById(R.id.button2);

        //绑定事件
        mBtnFileModel.setOnClickListener(this);
        mBtnStreamModel.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.button1:
                //跳转到文件模式
                startActivity(new Intent(this, FileActivity.class));
                break;
            case R.id.button2:
                //跳转到字节流模式
                startActivity(new Intent(this, StreamActivity.class));
                break;
        }
    }
}
