package cn.davidsu.customstickylivedata;

import android.arch.lifecycle.Observer;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //先发消息
        GlobalDataManager.getTestLiveData().setValue("发送消息");

        //再注册观察者

        //1.默认的非粘性注册
        GlobalDataManager.getTestLiveData().observe(this, new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                Toast.makeText(MainActivity.this, "第一个观察者为非粘性,所以接收不到注册前发送的消息", Toast.LENGTH_SHORT).show();
            }
        });

        //2.粘性注册
        GlobalDataManager.getTestLiveData().observe(this, new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                Toast.makeText(MainActivity.this, "第二个观察者为粘性,接收到了注册前发送的消息", Toast.LENGTH_SHORT).show();
            }
        }, true);
    }
}
