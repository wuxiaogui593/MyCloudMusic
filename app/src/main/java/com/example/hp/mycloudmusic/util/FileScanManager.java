package com.example.hp.mycloudmusic.util;

import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.util.Log;

import com.example.hp.mycloudmusic.musicInfo.AudioBean;
import com.litesuits.orm.LiteOrm;
import com.litesuits.orm.db.assit.QueryBuilder;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Long.parseLong;

public class FileScanManager {

    private static final String TAG = "FileScanManager";

    private LiteOrm mLiteOrm;
    private static FileScanManager mInstance;
    //当没有明确的对象作为锁，只是想让一段代码同步时，可以创建一个特殊的对象来充当锁
    private static final Object mLock = new Object();
    private static final Object wLock = new Object();


    public static FileScanManager getInstance(LiteOrm liteOrm){
        if(mInstance == null){
            synchronized (mLock){
                if(mInstance==null) {
                    mInstance = new FileScanManager(liteOrm);
                }
            }
        }
        return mInstance;
    }

    private FileScanManager(LiteOrm liteOrm){
        this.mLiteOrm = liteOrm;
    }


    private static final String SELECTION = MediaStore.Audio.AudioColumns.SIZE+">=? AND "+
            MediaStore.Audio.AudioColumns.DURATION+">=?";

    public List<AudioBean> scanMusic(Context context) {
        List<AudioBean> musicList = new ArrayList<>();

        String mFilterSize = SpUtils.getLocalFilterSize(context);
        String mFilterTime = SpUtils.getLocalFilterTime(context);

        long filterSize = parseLong(mFilterSize)*1024;
        long filterTime = parseLong(mFilterTime)*1000;

        Cursor cursor = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[] {
                        BaseColumns._ID,
                        MediaStore.Audio.AudioColumns.IS_MUSIC,
                        MediaStore.Audio.AudioColumns.TITLE,
                        MediaStore.Audio.AudioColumns.ARTIST,
                        MediaStore.Audio.AudioColumns.ALBUM,
                        MediaStore.Audio.AudioColumns.ALBUM_ID,
                        MediaStore.Audio.AudioColumns.DATA,         //文件路径
                        MediaStore.Audio.AudioColumns.DISPLAY_NAME, //文件名称
                        MediaStore.Audio.AudioColumns.SIZE,
                        MediaStore.Audio.AudioColumns.DURATION
                },
                SELECTION,
                new String[]{String.valueOf(filterSize),String.valueOf(filterTime)},
                MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
        if(cursor == null){
            return musicList;
        }

        while(cursor.moveToNext()){
            int isMusic = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.AudioColumns.IS_MUSIC));
            if(isMusic==0){
                continue;
            }
            AudioBean music = new AudioBean();
            music.setId(cursor.getLong(cursor.getColumnIndex(BaseColumns._ID)));
            music.setType(AudioBean.TYPE_LOCAL);
            music.setTitle(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.AudioColumns.TITLE)));
            music.setArtist(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.AudioColumns.ARTIST)));
            music.setAlbum(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.AudioColumns.ALBUM)));
            music.setAlbumId(cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.AudioColumns.ALBUM_ID)));
            music.setPath(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.AudioColumns.DATA)));
            music.setFileName(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.AudioColumns.DISPLAY_NAME)));
            music.setFileSize(cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.AudioColumns.SIZE)));
            music.setDuration(cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.AudioColumns.DURATION)));

            musicList.add(music);

            mLiteOrm.insert(music);
        }
        cursor.close();

        Log.e(TAG, "scanMusic------------------------------------------------------>");
        return musicList;

    }

    public List<AudioBean> getAudioFromDb() {
        List<AudioBean> musicList = new ArrayList<>();
        QueryBuilder<AudioBean> qb = new QueryBuilder<>(AudioBean.class);
        musicList = mLiteOrm.query(qb);
        Log.e(TAG, "getAudioFromDb------------------------------------------------->");
        return musicList;
    }
}
