package com.septem.firstapp.product;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;


/**
 * 文件存储系统的便利类，提供一些文件，目录的管理，文本文件的读取存储
 * 也可能会包括设定文本的读取。主要是一些静态方法。
 * Created by septem on 2016/7/4.
 * @author Septem
 * @version 0.1
 */
public class FileSystemManager {
    /**
     * 检测external storage是否可写
     * @return true or false
     */
    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if(Environment.MEDIA_MOUNTED.equals(state))
            return true;
        else return false;
    }

    /**
     * 检测external storage是否可读（或者可写表示也可读）
     * @return true or false
     */
    public static boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    /**
     * 存储文本内容到txt文件
     * @param content 要存储的文本内容
     * @param filePath 文件路径
     * @throws Exception IOException
     */
    public static void saveTxtFile(String content,String filePath) throws Exception {
        FileOutputStream fout = new FileOutputStream(filePath);
        OutputStreamWriter writer = new OutputStreamWriter(fout);
        writer.write(content);
        writer.close();
    }

    /**
     * 读取文本文件内容  ---建设中
     * @param filePath 文件路径
     * @throws Exception IOException
     */
    public static void readTxtFile(String filePath) throws Exception {
        FileInputStream fin = new FileInputStream(filePath);
        InputStreamReader instream = new InputStreamReader(fin);
        BufferedReader buffReader = new BufferedReader(instream);
        //under constructing

        instream.close();
    }

    /**
     * 从一个目录中读取所有的文件，包括子文件夹里面的
     * @param dir 读取文件的目录
     * @return 包含所有文件的数组
     */
    public static File[] getAllFileFromDir(File dir) {
        ArrayList<File> files = new ArrayList<File>(),dirs = new ArrayList<File>();
        dirs.add(dir);
        while(dirs.size()!=0)
        {
            File[] list = dirs.get(0).listFiles();
            for(File f :list)
            {
                if(f.isFile())
                    files.add(f);
                else dirs.add(f);
            }
            dirs.remove(0);
        }

        File[] output = {};
        output = files.toArray(output);

        return output;
    }

    /**
     * 从一个目录中读取所有的指定类型的文件，包括文件夹里面的
     * @param dir 读取文件的目录
     * @param extern 文件的扩展名，必须要扩".",例如读取Jpg文件，必须写成".jpg"
     * @return 包含结果的数组
     */
    public static File[] getAllTargetFile(File dir,String extern) {
        File[] files = getAllFileFromDir(dir);
        ArrayList<File> list = new ArrayList<File>();
        for(File f:files)
        {
            if(f.toString().endsWith(extern))
                list.add(f);
        }

        File[] output = {};
        output = list.toArray(output);

        return output;
    }

    /**
     * 检测文件类型
     * @param file 被检测文件
     * @param extension 扩展名，需要加“.”，例如检测是否是JPG文件，需要用".jpg"
     * @return 检测成功返回true，失败返回false
     */
    public static boolean isFileTypeOf(File file ,String extension) {
        if(file.toString().endsWith(extension))
            return true;
        else return false;
    }

    /**
     * 从uri转换成path。如果uri标识的是content，需要从数据库中读取然后才能获得真正的path
     * 所以建立这个便利方法,一般uri的scheme为content或者file可以用此解析，其他的还没有研究.
     * @param uri
     * @return 资源的路径path
     */
    public static String uriToPath(Context context,Uri uri) {
        if(uri==null)
            return null;
        String scheme = uri.getScheme();
        if(scheme==null||scheme.equals(ContentResolver.SCHEME_FILE))
            return uri.getPath();
        if(scheme.equals(ContentResolver.SCHEME_CONTENT)) {
            Cursor cursor = context.getContentResolver().query(
                    uri,
                    new String[]{MediaStore.Images.Media.DATA},
                    null,
                    null,
                    null
            );
            if(cursor!=null&&cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndex(
                        MediaStore.Images.Media.DATA
                ));
            }
        }
        return null;
    }
}
