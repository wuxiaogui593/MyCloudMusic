package com.example.hp.mycloudmusic.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.hp.mycloudmusic.musicInfo.AudioBean;
import com.example.hp.mycloudmusic.service.listener.OnPlayerEventListener;
import com.example.hp.mycloudmusic.service.receiver.NoisyAudioStreamReceiver;
import com.example.hp.mycloudmusic.util.AudioFocusManager;
import com.example.hp.mycloudmusic.util.NotificationUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PlayService extends Service {
    public static final String TAG = "PlayService";
    private boolean mReceiverTag = false;

    public int mPlayState = STATE_IDLE;

    private NoisyAudioStreamReceiver mNoisyReceiver = new NoisyAudioStreamReceiver();
    private IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    /**
     * 播放状态
     */
    public static final int STATE_IDLE = 100;
    public static final int STATE_PREPARING = 101;
    public static final int STATE_PLAYING = 102;
    public static final int STATE_PAUSE = 103;
    /**
     * 点击按钮类型
     */
    public static final String TYPE_PRE = "TYPE_PRE";
    public static final String TYPE_NEXT = "TYPE_NEXT";
    public static final String TYPE_START_PAUSE = "TYPE_START_PAUSE";

    public static final int UPDATE_PLAY_PROGRESS_SHOW = 0;

    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case UPDATE_PLAY_PROGRESS_SHOW:
                    updatePlayProgressShow();
                    break;
                default:
                    break;
            }
        }
    };


    /**
     * 正在播放的歌曲列表
     */
    private ArrayList<AudioBean> audioMusics;

    /**
     * 正在播放的歌曲序号
     */
    private int mPlayingPosition = -1;
    /**
     * 正在播放的歌曲
     */
    private AudioBean mPlayingMusic;
    /**
     * 播放器
     */
    private OnPlayerEventListener mPlayerEventListener;
    private MediaPlayer mPlayer;
    private AudioFocusManager mAudioFocusManager;

    private PlayBinder mBinder = new PlayBinder();

    public void setOnPlayerEventListener(OnPlayerEventListener playerEventListener) {
        mPlayerEventListener = playerEventListener;
    }

    /**
     * 显示意图
     * 耳机，蓝牙断开
     * @param context
     * @param type
     */
    public static void startCommand(Context context, String type) {
        Intent intent = new Intent(context,PlayService.class);
        intent.setAction(type);
        context.startService(intent);
    }

    public int getCurrentPosition() {
        return mPlayer.getCurrentPosition();
    }

    public class PlayBinder extends Binder{

        public PlayService getPlayService(){
            return PlayService.this;
        }


    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, "onCreate: service is on create!");
        audioMusics = new ArrayList<>();
        NotificationUtils.init(this);       //初始化NotificationUtils
        createMediaPlayer();
        initAudioFocusManager();
    }
    private void initAudioFocusManager() {
        mAudioFocusManager = new AudioFocusManager(this);
    }

    private void createMediaPlayer() {
        //先判断是否为空
        if(mPlayer == null){
            mPlayer = new MediaPlayer();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent!=null && intent.getAction()!=null){
            switch (intent.getAction()){
                case TYPE_START_PAUSE:
                    playPause();
                    break;
                default:
                    break;
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private void playPause() {

    }


    public void play(List<AudioBean> musicList, int position) {
        Log.e(TAG, "play: 开始设置mediaplayer");
        if(musicList==null || musicList.size()==0 || position<0){
            return;
        }
        //此处应分开写
        if(audioMusics == null){
            audioMusics = new ArrayList<>();
        }
        if(!audioMusics.isEmpty()){
            audioMusics.clear();
        }
        //为止
        audioMusics.addAll(musicList);
        mPlayingPosition = position;
        mPlayingMusic = audioMusics.get(mPlayingPosition);

        createMediaPlayer();
        try {
            mPlayer.reset();
            mPlayer.setDataSource(mPlayingMusic.getPath());     //catch
            mPlayer.prepareAsync();

            mPlayState = STATE_PREPARING;

            mPlayer.setOnPreparedListener(mOnPreparedListener);
            mPlayer.setOnBufferingUpdateListener(mOnBufferingUpdateListener);
            mPlayer.setOnCompletionListener(mOnCompletionListener);
            mPlayer.setOnSeekCompleteListener(mOnSeekCompleteListener);
            mPlayer.setOnErrorListener(mOnErrorListener);
            mPlayer.setOnInfoListener(mOnInfoListener);
            if(mPlayerEventListener != null){
                Log.e(TAG, "服务调用onChange方法");
                mPlayerEventListener.onChange(mPlayingMusic);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**-------------------------------mediaPlayer监听方法------------------------------------------*/
    /**
     * 流载入完成后调用start方法
     */
    private MediaPlayer.OnPreparedListener mOnPreparedListener = new MediaPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(MediaPlayer mp) {
            if(isPreparing()){
                start();
            }
        }
    };
    /**
     * 网络流缓冲更新
     */
    private MediaPlayer.OnBufferingUpdateListener mOnBufferingUpdateListener = new MediaPlayer.OnBufferingUpdateListener() {
        @Override
        public void onBufferingUpdate(MediaPlayer mp, int percent) {
            if(mPlayerEventListener != null){
                mPlayerEventListener.onBufferingUpdate(percent);
            }
        }
    };
    private MediaPlayer.OnCompletionListener mOnCompletionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
            next();
        }
    };
    private MediaPlayer.OnSeekCompleteListener mOnSeekCompleteListener = new MediaPlayer.OnSeekCompleteListener() {
        @Override
        public void onSeekComplete(MediaPlayer mp) {

        }
    };
    private MediaPlayer.OnErrorListener mOnErrorListener = new MediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            return false;
        }
    };
    private MediaPlayer.OnInfoListener mOnInfoListener = new MediaPlayer.OnInfoListener() {
        @Override
        public boolean onInfo(MediaPlayer mp, int what, int extra) {
            return false;
        }
    };


    /**-----------------------------------开始播放，暂停，停止--------------------------------**/
    private void start() {
        Log.e(TAG, "start: 开始播放");
        if(!isPreparing() && !isPausing()){
            return;
        }
        if(mPlayingMusic == null){
            return;
        }
        //获取焦点
        if(mAudioFocusManager.requestAudioFucos()){
            if(mPlayer != null){
                mPlayer.start();
                mPlayState = STATE_PLAYING;
                //开始循环发送消息，更新进度条
                handler.sendEmptyMessage(UPDATE_PLAY_PROGRESS_SHOW);
                if(mPlayerEventListener != null){
                    mPlayerEventListener.onPlayerStart();
                }
                //注册监听耳机/蓝牙,避免多次注册，且之后在pause中注销
                if(!mReceiverTag){
                    mReceiverTag = true;
                    registerReceiver(mNoisyReceiver,filter);
                }

            }
        }

    }

    private void next() {

    }


    /**-------------------------------------------------------------------------------------------*/
    /**
     * 更新进度条和时间
     */
    private void updatePlayProgressShow() {
        if(isPlaying() && mPlayerEventListener!=null){
            int currentPosition= mPlayer.getCurrentPosition();
            Log.d(TAG, "PlayService 调用activity onUpdateProgress方法 ");
            mPlayerEventListener.onUpdateProgress(currentPosition);
        }
        Log.d(TAG, "updatePlayProgressShow" );
        handler.sendEmptyMessageDelayed(UPDATE_PLAY_PROGRESS_SHOW,300);
    }

    private boolean isPausing() {
        return mPlayState == STATE_PAUSE;
    }

    private boolean isPlaying(){
        return mPlayState == STATE_PLAYING;
    }

    private boolean isIdle(){
        return mPlayState == STATE_IDLE;
    }

    private boolean isPreparing() {
        return mPlayState == STATE_PREPARING;
    }

    public void setMusicList(List<AudioBean> list) {
        audioMusics.clear();
        audioMusics.addAll(list);
    }

    public ArrayList<AudioBean> getLocalMusic(){
        return audioMusics;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
