package audiorecord.wtt.com.audiorecord;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Administrator on 2017/4/28.
 */

public class StreamActivity extends AppCompatActivity implements View.OnClickListener{

    private Button mBtnPlay;
    private TextView mTvMark;
    private Button mBtnSay;
    //录音状态 ,volatle 保证多线程内存同步，避免出现问题
    private volatile boolean mIsRecording = false;
    private volatile boolean mIsPlay = false;
    private Handler mMainThreadHandler = null;
    private ExecutorService mExecutorService;
    private AudioRecord mAudioRecord;
    private FileOutputStream mFileOutputStream;
    private File mAudioFile;
    private long mStartRecordTime, mStopRecordTime;

    private AudioTrack mAudioTrack;

    //buffer 不能太大
    private static final int BUFFER_SIZE = 2048;
    private byte[] mBuffer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);
        mMainThreadHandler = new Handler(Looper.getMainLooper());
        mBuffer = new byte[BUFFER_SIZE];
        //录音JNI 函数不具备线程安全性，所以要用单线程
        mExecutorService = Executors.newSingleThreadExecutor();
        //初始化View
        mBtnPlay = (Button) findViewById(R.id.btn_play);
        mTvMark = (TextView) findViewById(R.id.tv_mark);
        mBtnSay = (Button) findViewById(R.id.btn_say);
        mBtnSay.setOnClickListener(this);
        mBtnPlay.setOnClickListener(this);

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
     * 开始播放
     * @param mAudioFile
     */
    private void doPlay(File mAudioFile) {

        //从文件流读取数据
        FileInputStream mFileInputStream = null;

        try {
            //配置播放器
            //初始化streamType,扬声器播放
            int streamType = AudioManager.STREAM_MUSIC;
            //通用采样率
            int sampleRateInHz = 44100;
            //单声道
            int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
            //通用16bit
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            //最小Buffer
            int bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
            //流模式
            int mode = AudioTrack.MODE_STREAM;
            mAudioTrack = new AudioTrack(streamType, sampleRateInHz, channelConfig, audioFormat,
                    //不能小于最低要求，也不能小于读取数量
                    Math.max(bufferSizeInBytes, BUFFER_SIZE), mode);
            mAudioTrack.play();
            //从文件流读取数据
            mFileInputStream = new FileInputStream(mAudioFile);
            int read = 0;
            //循环读取数据
            while ((read = mFileInputStream.read(mBuffer))>0){
                int ret = mAudioTrack.write(mBuffer, 0, read);
                switch (ret){
                    case AudioTrack.ERROR_INVALID_OPERATION:
                    case AudioTrack.ERROR_DEAD_OBJECT:
                    case AudioTrack.ERROR_BAD_VALUE:
                        playFail();
                        return;
                    default:
                        break;
                }
            }
        }catch (RuntimeException | IOException  e ) {
            //异常处理
            e.printStackTrace();
            //提示失败
            playFail();
        }finally {
            mIsPlay = false;

            //关闭流
            closeStream(mFileInputStream);
            //释放播放器
            stopPlay();
        }
    }

    /**
     * 停止播放
     */
    private void stopPlay() {

        if(mAudioTrack!=null){
            try {
                mAudioTrack.stop();
                mAudioTrack.release();
                mAudioTrack = null;
            }catch (RuntimeException e){
                e.printStackTrace();
            }
        }
    }

    /**
     * 静默关闭
     * @param mFileInputStream
     */
    private void closeStream(FileInputStream mFileInputStream){
        try{
            mFileInputStream.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 提示用户
     */
    private void playFail() {
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                //给用户toast提示失败，必须在主线程内执行，否则显示不出来
                Toast.makeText(StreamActivity.this, "播放失败", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //activity销毁时，释放资源避免内存泄漏
        mExecutorService.shutdownNow();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_say:
                //根据当前状态改变UI，执行开始/停止录音的逻辑
                if(mIsRecording){
                    mBtnSay.setText("开始");
                    //改变状态
                    mIsRecording = false;

                }else{
                    mBtnSay.setText("停止");

                    //改变状态
                    mIsRecording = true;

                    //提交后台处理执行录音逻辑
                    mExecutorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            //执行开始录音逻辑，失败提示用户
                            if(!startRecord()){
                                recordFail();
                            }
                        }
                    });

                }
                break;
            case R.id.btn_play:
                play();
                break;
        }
    }

    /**
     * 启动录音逻辑
     * @return
     */
    private boolean startRecord() {

        try {
            //创建录音文件
            mAudioFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/iRecorderTemp/"+ System.currentTimeMillis() + ".pcm");
            mAudioFile.getParentFile().mkdirs();
            mAudioFile.createNewFile();
            //创建文件输出流
            mFileOutputStream = new FileOutputStream(mAudioFile);

            //配置AudioRecord
            int audioSource = MediaRecorder.AudioSource.MIC;
            //所有安卓设备都支持的比率
            int sampleRate = 44100;
            //单声道输入
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;
            //PCM16是所有Android系统都支持的
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

            //计算AudioRecord 内部Buffer最小大小
            int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

            mAudioRecord = new AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, Math.max(minBufferSize, BUFFER_SIZE));

            //开始录音
            mAudioRecord.startRecording();
            //记录开始录音时间，用于统计时长
            mStartRecordTime = System.currentTimeMillis();
            //循环读取数据写到输出流中
            while (mIsRecording){
                //只要还在录音状态，就一直读取数据
                int read = mAudioRecord.read(mBuffer, 0,BUFFER_SIZE);
                if(read>0){
                    mFileOutputStream.write(mBuffer, 0, read);
                }else{
                    //读取数据失败，返回false提示用户
                    return false;
                }
            }
            //退出循环，停止录音，释放资源
            return stopRecord();
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            return false;
        } finally {
            //释放AudioRecord
            releaseRecord();
        }
    }

    /**
     * 释放资源
     */
    private void releaseRecord() {
        if(mAudioRecord!=null){
            mAudioRecord.release();
            mAudioRecord = null;
        }
    }

    /**
     * 结束录音逻辑
     * @return
     */
    private boolean stopRecord() {
        try{
            //结束录音
            mAudioRecord.stop();
            releaseRecord();
            //记录时间统计时长
            mFileOutputStream.close();
            //关闭流
            mStopRecordTime = System.currentTimeMillis();

            // 只接受超过3秒的录音
            final int second = (int) ((mStopRecordTime-mStartRecordTime)/1000);
            Log.e("=====>", "second:"+second);
            if(second>3){
                //在UI上显示出来
                mMainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTvMark.setText(mTvMark.getText() + "\n录音成功 " + second + "秒");
                    }
                });
            }
        }catch (IOException | RuntimeException e){
            e.printStackTrace();
        }

        return true;
    }

    private void recordFail() {
        mAudioFile = null;
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                //给用户toast提示失败，必须在主线程内执行，否则显示不出来
                Toast.makeText(StreamActivity.this, "录音失败", Toast.LENGTH_LONG).show();
            }
        });
    }

}
