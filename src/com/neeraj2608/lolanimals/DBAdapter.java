package com.neeraj2608.lolanimals;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DBAdapter {
	private static final String TAG = "LolCats";
	public static final String KEY_ROWID = "_id";
	public static final String KEY_URL = "url";
	public static final String KEY_CACHEFILENAME = "cachefilename"; //stores the filename of the corresponding record's
																	//cached image file. See comments for insertCacheFileName function
	
	private static final String DATABASE_FILENAME = "urls.db";
	private static final String DATABASE_TABLE = "urls";
	private static final int DATABASE_VERSION = 1;
	
	private static int max_rowID = 1; //Stores id of next record to be stored
									  //See comments of function deleteURL
	
	// The URL field has a UNIQUE constraint
	private static final String DATABASE_CREATE =
		"create table urls (_id integer, " + "url text not null, " + "cachefilename text, " + "unique(url));";
	
	private final Context context;
	private DatabaseHelper DBHelper;
	private SQLiteDatabase db;
	
	public DBAdapter(Context context){
		this.context = context;
		DBHelper = new DatabaseHelper(context);
	}
	
	// ------------------------------------------
	// Creates a database instance if none exists
	// ------------------------------------------
	public DBAdapter open() throws SQLException {
		db = DBHelper.getWritableDatabase();
		
		// Make sure the index starts off right if there is a preexisting table
		Cursor mCursor = db.query(DATABASE_TABLE, new String[] {KEY_ROWID}, KEY_ROWID + "=" + max_rowID++, null, null, null, null);
		while(mCursor.moveToFirst()){
			mCursor = db.query(DATABASE_TABLE, new String[] {KEY_ROWID}, KEY_ROWID + "=" + max_rowID++, null, null, null, null);
		}
		max_rowID--;
		Log.d(TAG,"Next record stored will have id: " + max_rowID);
		
		mCursor.close();
		return this;
	}
	
	// ------------------------------
	// Open an existing database file
	// ------------------------------
	public DBAdapter openDatabase(String path) throws SQLException {
		db = DBHelper.openDatabase(path);
		return this;
	}
	
	// -----------------------
	// Close any open database
	// -----------------------
	public void close(){
		DBHelper.close();
	}
	

	// ----------------
	// Insert a new URL
	// ----------------
	public long insertURL(String URL){
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_URL, URL);
		initialValues.put(KEY_ROWID, max_rowID);
		max_rowID++;
		return db.insert(DATABASE_TABLE, null, initialValues);
	}
	
	// -------------------------------------------------------
	// Retrieve the URL column of a record given its _id field
	// -------------------------------------------------------
	public Cursor getURL(long rowID){
		Cursor mCursor = db.query(DATABASE_TABLE, new String[] {KEY_URL}, KEY_ROWID + "=" + rowID, null, null, null, null);
		if(mCursor.moveToFirst()){
			return mCursor;			
		} else {
			return null; //Cursor was empty
		}
	}
	
	// ------------------------------------------------------------------
	// This function stores a unique, device timestamp-dependent filename
	// generated in bookMarkActivity for each record
	// ------------------------------------------------------------------
	public boolean insertCacheFileName(long rowID, String cachefilename){
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_CACHEFILENAME, cachefilename);
		return (db.update(DATABASE_TABLE, initialValues, KEY_ROWID + "=" + rowID, null) > 0);
	}
	
	// -----------------------------------------------------------------
	// Retrieve the cachefilename column of a record given its _id field
	// -----------------------------------------------------------------
	public Cursor getCacheFileName(long rowID){
		Cursor mCursor = db.query(DATABASE_TABLE, new String[] {KEY_CACHEFILENAME}, KEY_ROWID + "=" + rowID, null, null, null, null);
		if(mCursor.moveToFirst()){
			return mCursor;			
		} else {
			return null; //Cursor was empty
		}
	}
	
	// -----------------------------------------------------------------------------
	// Every time a record is deleted, the ids of all successive fields
	// are updated to ensure that the id column stays a sequential, ascending
	// series. For example, assume the table looks like:
	// _id	url		cachefilename
	// 1	url1	a.jpg
	// 2	url2	b.jpg
	// 3	url3	c.jpg
	// 4	url4	d.jpg
	// max_rowID will always be the value that the _id field of the next record to be
	// inserted should take. Thus, in this case, max_rowID is 5.
	//
	// Now, let's say url2 is deleted. Then, the id field of any records following url2
	// will be updated to ensure that the _id column stays a sequentially ascending
	// series. Thus, after deletion, the table will be updated to:
	// _id	url		cachefilename
	// 1	url1	a.jpg
	// 2	url3	c.jpg
	// 3	url4	d.jpg
	// max_rowID is updated to 4.
	// -----------------------------------------------------------------------------
	public boolean deleteURL(long rowID){
		boolean result = (db.delete(DATABASE_TABLE,KEY_ROWID + "=" + rowID,null) > 0);
		Cursor mCursor = db.query(DATABASE_TABLE, new String[] {KEY_ROWID}, KEY_ROWID + "=" + ++rowID, null, null, null, null);
		while(mCursor.moveToFirst()){
			ContentValues initialValues = new ContentValues();
			initialValues.put(KEY_ROWID, rowID-1);
			db.update(DATABASE_TABLE, initialValues, KEY_ROWID + "=" + rowID, null);
			Log.d(TAG,"Now updating row: " + rowID);
			mCursor = db.query(DATABASE_TABLE, new String[] {KEY_ROWID}, KEY_ROWID + "=" + ++rowID, null, null, null, null);
		}
		max_rowID = (int) rowID-1;
		mCursor.close();
		return result;
	}

	// ---------------------------------------------------------------
	// Helper class to manage opening and closing of database instance
	// ---------------------------------------------------------------
	private static class DatabaseHelper extends SQLiteOpenHelper{

		public DatabaseHelper(Context context) {
			super(context, DATABASE_FILENAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DATABASE_CREATE);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.d(TAG, "Upgrading database from version " + oldVersion + " to version " + newVersion);
			db.execSQL("DROP TABLE IF EXISTS urls");
			onCreate(db);
		}
		
		public SQLiteDatabase openDatabase(String path){
			return SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE);
		}
	}
}
