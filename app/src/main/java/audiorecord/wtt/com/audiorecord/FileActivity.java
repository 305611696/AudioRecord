package audiorecord.wtt.com.audiorecord;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Administrator on 2017/4/28.
 */

public class FileActivity extends AppCompatActivity{

    private Button mBtnPlay;
    private TextView mTvMark;
    private Button mBtnSay;
    private ExecutorService mExecutorService;

    private Handler mUpdateUIHandler;
    private MediaRecorder mMediaRecorder;
    private File mAudioFile;
    private long mStartRecordTime, mStopRecordTime;

    private volatile boolean mIsPlay = false;
    private MediaPlayer mMediaPlayer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file);
        mUpdateUIHandler = new Handler(Looper.getMainLooper());
        //初始化View
        mBtnPlay = (Button) findViewById(R.id.btn_play);
        mTvMark = (TextView) findViewById(R.id.tv_mark);
        mBtnSay = (Button) findViewById(R.id.btn_say);

        //录音 JNI 不具备线程安全性，所以要用单线程
        mExecutorService = Executors.newSingleThreadExecutor();

        mBtnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play();
            }
        });

        //按住说话 释放发送 所以不需要 OnClicklistener
        mBtnSay.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //根据不同 touch action 执行不同操作
                switch (event.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        //开始录音
                        startRecorder();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        //停止录音
                        stopRecorder();
                        break;
                }

                return true;
            }
        });
    }

    /**
     *  播放
     */
    private void play() {
        //判断文件是否为空，判断是否是播放状态
        if(mAudioFile!=null&&!mIsPlay){
            //改变播放状态
            mIsPlay = true;

            //提交后台任务播放音频
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    doPlay(mAudioFile);
                }
            });
        }
    }

    /**
     * 执行播放
     * @param mAudioFile
     */
    private void doPlay(File mAudioFile) {
        try {
            //配置Mediaplayer
            mMediaPlayer = new MediaPlayer();
            //设置声音文件
            mMediaPlayer.setDataSource(mAudioFile.getAbsolutePath());
            //监听事件回调
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    //播放结束，释放播放器
                    stopPlay();
                }
            });
            //异常捕获
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {

                    //提示用户
                    playFail();
                    //停止播放器
                    stopPlay();
                    //错误已经处理返回true
                    return true;
                }
            });

            //配置音量是否循环
            mMediaPlayer.setVolume(1,1);
            mMediaPlayer.setLooping(false);
            //准备开始
            mMediaPlayer.prepare();
            mMediaPlayer.start();
        }catch (RuntimeException | IOException e){
            e.printStackTrace();
            //错误提示用户
            playFail();
            stopPlay();
        }
    }

    /**
     * 停止播放
     */
    private void stopPlay() {
        mIsPlay = false;
        if(mMediaPlayer!=null){
            mMediaPlayer.setOnCompletionListener(null);
            mMediaPlayer.setOnErrorListener(null);
            mMediaPlayer.stop();
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    /**
     * 提示用户
     */
    private void playFail() {
        mUpdateUIHandler.post(new Runnable() {
            @Override
            public void run() {
                //给用户toast提示失败，必须在主线程内执行，否则显示不出来
                Toast.makeText(FileActivity.this, "播放失败", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //activity 销毁时停止后台任务，防止内存泄漏
        mExecutorService.shutdownNow();
        releaseRecorder();
        stopPlay();
    }

    /**
     * 开始录音
     * */
    private void startRecorder() {
        //改变 UI 状态
        mBtnSay.setText(R.string.start);

        //提交后台任务，执行录音逻辑
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                //释放之前录音的recorder
                releaseRecorder();

                //执行录音逻辑，如果失败提示用户
                if(!doStart()){
                    recordFail();
                }
            }
        });
    }

    /***
     * 停止录音
     * */
    private void stopRecorder() {
        //改变 UI 状态
        mBtnSay.setText(R.string.presstosay);

        //提交后台任务，执行停止逻辑
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                //执行停止录音逻辑，失败提示用户
                if(!doStop()){
                    //提示失败
                    recordFail();
                }

                //释放MediaRecorder
                releaseRecorder();
            }
        });
    }

    /**
     * 释放MediaRecorder
     */
    private void releaseRecorder() {
        if(mMediaRecorder!=null){
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    /**
     * 启动录音逻辑
     */
    private boolean doStart() {

        try {
            //创建MediaRecorder
            mMediaRecorder = new MediaRecorder();

            //创建录音文件
            mAudioFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/iRecorderTemp/"+ System.currentTimeMillis() + ".m4a");
            mAudioFile.getParentFile().mkdirs();
            mAudioFile.createNewFile();
            //配置MediaRecorder
            //从麦克风采集
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            //保存文件为MP4文件格式
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            //所有安卓系统都支持的采样率
            mMediaRecorder.setAudioSamplingRate(44100);
            //文件编码
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            //设置音频编码采样频率
            mMediaRecorder.setAudioEncodingBitRate(96000);

            //设置录音文件位置
            mMediaRecorder.setOutputFile(mAudioFile.getAbsolutePath());

            //执行开始录音
            mMediaRecorder.prepare();
            mMediaRecorder.start();

            //记录开始录音时间，用于统计时长
            mStartRecordTime = System.currentTimeMillis();
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    /***
     * 停止录音逻辑
     * */
    private boolean doStop() {
        try {
            //停止录音
            mMediaRecorder.stop();

            //统计时长
            mStopRecordTime = System.currentTimeMillis();

            // 只接受超过3秒的录音
            final int second = (int) ((mStopRecordTime-mStartRecordTime)/1000);
            Log.e("=====>", "second:"+second);
            if(second>3){
                //在UI上显示出来
                mUpdateUIHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTvMark.setText(mTvMark.getText() + "\n录音成功 " + second + "秒");
                    }
                });
            }

        }catch (RuntimeException e){
            e.printStackTrace();
            return false;
        }
        //停止成功
        return  true;
    }

    /**
     * 录音错误的处理
     */
    private void recordFail() {
        mAudioFile = null;
        mUpdateUIHandler.post(new Runnable() {
            @Override
            public void run() {
                //给用户toast提示失败，必须在主线程内执行，否则显示不出来
                Toast.makeText(FileActivity.this, "录音失败", Toast.LENGTH_LONG).show();
            }
        });

    }

}
