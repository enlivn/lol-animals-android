package com.neeraj2608.lolanimals;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.Toast;

public class bookMarkActivity extends Activity{
	// Thread handling variables
	private static final int BUSY_DRAWING_ID = 1;
	private BusyDrawingThread busyDrawingThread;
	private ProgressDialog busyDrawingThreadProgressDialog;
	private static final String TAG = "LolCats"; //Debugging only

	// Image display variables
	private Bitmap imgBitMap; //for displaying and saving the image
	private ImageView imgView; //displays the pictures
	private ImageButton nextImgBtn, unbookmarkImgBtn, saveImgBtn, prevImgBtn; //next image button
	private InputStream imgStream;
	private BufferedInputStream bufferedimgStream;
	private GestureDetector flingDetector;
	private ImageSwitcher imageSwitcher;
	private Animation slideLeftIn;
	private Animation slideRightIn;
	private Animation slideLeftOut;
	private Animation slideRightOut;
	private final static int SWIPE_MAX_OFF_PATH = 250;
	private final static int SWIPE_MIN_DISTANCE = 120;
	private final static int SWIPE_THRESHOLD_VELOCITY = 200;
	private SimpleDateFormat uniqueFileName = new SimpleDateFormat("ddMMyyyyhhmmSSS");
	File bookmarksCacheDir = null;
	File lolCatsSaveDir = null;
	private static int imageCeiling; //the maximum number of new images displayed so far
	private static int currDispImage; //image currently on screen (could be from the cache or the Internet)
									  //currDispImage <= imageCeiling
	private final int CACHE_MODE = 0;
	private final int SAVE_MODE = 1;
	private DBAdapter db;
	private URL imgURL;
	private Cursor mCursor;
		
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.bookmarkmain);
		
		// --------------------------
		// Check network connectivity
		// --------------------------
		checkNetworkAvailable(bookMarkActivity.this);

		// -----------------------------------
		// Open the database file; if it does
		// not exist, exit the activity
		// -----------------------------------
		try{
			final String databasePath = this.getDatabasePath("urls.db").toString();
			db = new DBAdapter(this);
			db.openDatabase(databasePath);
			mCursor = db.getURL(1);
			if(mCursor == null){ //database is empty; exit activity
				Log.d(TAG,"Database file opened but contained no image records");
				Toast.makeText(bookMarkActivity.this, "There are no bookmarked images", Toast.LENGTH_SHORT).show();
				bookMarkActivity.this.finish();
			} else { //database is okay and contains images; life can go on
				Toast greeting = Toast.makeText(this,"Displaying Only Bookmarked Images", Toast.LENGTH_LONG);
				greeting.setGravity(Gravity.CENTER, 0, 0);
				greeting.show();
			}
		} catch(SQLException e) {
			Log.d(TAG,"SQLiteException while opening database file");
			Toast.makeText(bookMarkActivity.this, "There are no bookmarked images", Toast.LENGTH_SHORT).show();
			bookMarkActivity.this.finish();
		}
		
		// ----------------------------
		// Configure Fetch Image Button
		// ----------------------------
		nextImgBtn = (ImageButton) findViewById(R.id.nextImgBtn);
		nextImgBtn.setOnClickListener(handleNextImgBtn);
		
		// ---------------------------
		// Configure Save Image Button
		// ---------------------------
		saveImgBtn = (ImageButton) findViewById(R.id.saveImgBtn);
		saveImgBtn.setOnClickListener(handleSaveImgBtn);
		
		// -------------------------------
		// Configure Bookmark Image Button
		// -------------------------------
		unbookmarkImgBtn = (ImageButton) findViewById(R.id.unbookmarkImgBtn);
		unbookmarkImgBtn.setOnClickListener(handleunbookmarkImgBtn);
		
		// -------------------------------
		// Configure Previous Image Button
		// -------------------------------
		prevImgBtn = (ImageButton) findViewById(R.id.prevImgBtn);
		prevImgBtn.setOnClickListener(handleprevImgBtn);

		// ---------------------------------------
		// Clean old cache and initialize counters
		// ---------------------------------------
		bookmarksCacheDir = new File(this.getCacheDir(), "bookmarkCache");
		lolCatsSaveDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
		eraseDir(bookmarksCacheDir); //clear all files in the cache
		bookmarksCacheDir.mkdirs(); //re-create the top-level cache folder that was deleted by eraseDir
		imageCeiling = currDispImage = 0;
		
		// ----------------------------
		// Configure Image display area
		// ----------------------------
		imgView = (ImageView) findViewById(R.id.imgShow);
		
		// ------------------------------------------------
		// Configure startup images area with fling support
		// ------------------------------------------------
		imageSwitcher = (ImageSwitcher) findViewById(R.id.imageSwitcher);
		slideLeftIn = AnimationUtils.loadAnimation(this, R.anim.slide_left_in);
		slideRightIn = AnimationUtils.loadAnimation(this, R.anim.slide_right_in);
		slideLeftOut = AnimationUtils.loadAnimation(this, R.anim.slide_left_out);
		slideRightOut = AnimationUtils.loadAnimation(this, R.anim.slide_right_out);
		flingListenerClass flingListener = new flingListenerClass(); //listener callback for GestureDetector
		flingDetector = new GestureDetector(bookMarkActivity.this, flingListener);
		
	}
	
	/**********************************************
	 * * * * * * * * * * * * * * * * * * * * * * *
	 * CHECK NETWORK CONNECTIVITY
	 * * * * * * * * * * * * * * * * * * * * * * *
	**********************************************/
	private void checkNetworkAvailable(Context ctx) {
		ConnectivityManager connManService = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo wifiNetworkInfo = connManService.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		NetworkInfo mobileNetworkInfo = connManService.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
		if((!wifiNetworkInfo.isConnected())&& (!mobileNetworkInfo.isConnected())){
			Log.d(TAG,"Wifi/Mobile data not connected");
			AlertDialog.Builder alertBuilder = new AlertDialog.Builder(bookMarkActivity.this);
			alertBuilder.setTitle("No network connection")
						.setMessage("Please check your network connection.\n" +
								"A 3G or WiFi connection is recommended for this application.")
						.setIcon(R.drawable.nonetwork)
			       		.setCancelable(false)
			       		.setNeutralButton("OK", new DialogInterface.OnClickListener() {
			       			public void onClick(DialogInterface dialog, int id) {
			       				setResult(RESULT_OK);
			       				bookMarkActivity.this.finish();
			       			}
			       		});
			AlertDialog alert = alertBuilder.create();
			alert.show();
		}
	}
	
	/**********************************************
	 * * * * * * * * * * * * * * * * * * * * * * *
	 * OPTIONS MENU
	 * * * * * * * * * * * * * * * * * * * * * * *
	**********************************************/
	@Override
	public boolean onCreateOptionsMenu(Menu m){
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.bookmarkmenu, m);
		return true;
	}
	
	private void eraseDir(File fileToDelete) {
		if(fileToDelete != null && fileToDelete.isDirectory()){
			String[] files = fileToDelete.list();
			for(int  i = 0; i < files.length; i++){
				eraseDir(new File(fileToDelete, files[i]));
			}
		}
		fileToDelete.delete();
	}
	
	//---------------------------------------------
	// Handle options menu selections
	//---------------------------------------------
	public boolean onOptionsItemSelected(MenuItem item){
		switch(item.getItemId()){
		case R.id.optionsMenuQuit:
			setResult(RESULT_OK);
			this.finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	/**********************************************
	 * * * * * * * * * * * * * * * * * * * * * * * 
	 * BUTTONS AND BUTTON HANDLERS
	 * * * * * * * * * * * * * * * * * * * * * * *
	**********************************************/
	
	//---------------------------------------------
	// Handler for Fetch Image Button Clicks
	//---------------------------------------------
	OnClickListener handleNextImgBtn = new OnClickListener() {
		public void onClick(View view) {
			if(nextImgBtn.isHapticFeedbackEnabled()){
				nextImgBtn.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY,
						android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); //notify the user that a successful tap has been registered
			}
			displayNewImage();
		}
	};

	// ---------------------------------
	// Handler for Save Image Button Clicks
	// ---------------------------------
	OnClickListener handleSaveImgBtn = new OnClickListener() {
		public void onClick(View view) {
			if(saveImgBtn.isHapticFeedbackEnabled()){
				saveImgBtn.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY,
						android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); //notify the user that a successful tap has been registered
			}
			if(imageCeiling > 0){
				saveImage(lolCatsSaveDir, SAVE_MODE);
			} else {
				Toast.makeText(bookMarkActivity.this, "No image displayed", Toast.LENGTH_SHORT).show();
			}
		}
	};
	
	// --------------------------------------------------
	// Handler for unbookmark Image Button Clicks
	// --------------------------------------------------
	OnClickListener handleunbookmarkImgBtn = new OnClickListener() {
		public void onClick(View view) {
			if(unbookmarkImgBtn.isHapticFeedbackEnabled()){
				unbookmarkImgBtn.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY,
						android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); //notify the user that a successful tap has been registered
			}
			if(imageCeiling > 0){
				db.open();
				mCursor = db.getCacheFileName(currDispImage);
				if(mCursor != null){
					String cacheFileName = new String(mCursor.getString(mCursor.getColumnIndex("cachefilename")));
					mCursor.close();

					// make a test to see if we fall off the map
					int testID = currDispImage + 1;
					mCursor = db.getURL(testID);
					db.deleteURL(currDispImage);
					imageCeiling--; //the total number of images has been reduced by one
					db.close();
					if(mCursor != null){
						mCursor.close();
						currDispImage--;//since the table indices have been refreshed, the same number now points
										//to a new image. In order for displayNewImage (called below)
						 				//to get the same number, we decrement currDispImage (because displayNewImage
						 				//will increment it before displaying the next image)
						displayNewImage(); //there are more images after this one so it's safe to proceed forwards
					}
					else{
						if(currDispImage > 1){
							displayOldImage();  //the image we just deleted was the last bookmarked image
												//so we must proceed backwards.
						} else {
							imgView.setBackgroundDrawable(null);
							Toast.makeText(bookMarkActivity.this, "There are no bookmarked images", Toast.LENGTH_SHORT).show();
							bookMarkActivity.this.finish();
						}
					}
					
					File delImgFile = new File(bookmarksCacheDir, cacheFileName);
					delImgFile.delete();
				}
				Log.d(TAG,"handleunbookmark: " + currDispImage + " " + imageCeiling); //Debugging only
			}  else {
				Toast.makeText(bookMarkActivity.this, "No image displayed", Toast.LENGTH_SHORT).show();
			}
		}
	};
	
	//---------------------------------------------
	// Handler for Previous Image Button Clicks
	//---------------------------------------------
	OnClickListener handleprevImgBtn = new OnClickListener() {
		public void onClick(View view) {
			if(prevImgBtn.isHapticFeedbackEnabled()){
				prevImgBtn.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY,
						android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); //notify the user that a successful tap has been registered
			}
			if(imageCeiling > 0){
				displayOldImage();
			}  else {
				Toast.makeText(bookMarkActivity.this, "No previous images to show", Toast.LENGTH_SHORT).show();
			}
		}
	};
	
	/**********************************************
	 * * * * * * * * * * * * * * * * * * * * * * *
	 * IMAGE DISPLAY FUNCTIONS
	 * * * * * * * * * * * * * * * * * * * * * * *
	**********************************************/
	private void displayNewImage(){
		if(currDispImage == imageCeiling){ //the cached images have caught up to the displayed images and we can
										   //now fetch new images
				showDialog(BUSY_DRAWING_ID);
		}
		else { //currDispImage < imageCeiling and we still have cached images to display
			if(imageCeiling > 0){
				db.open();
				mCursor = db.getCacheFileName(++currDispImage);
				db.close();

				String cacheFileName = new String(mCursor.getString(mCursor.getColumnIndex("cachefilename")));
				mCursor.close();
				File dispImgFile = new File(bookmarksCacheDir, cacheFileName);
				imgBitMap = BitmapFactory.decodeFile(dispImgFile.toString());
				imgView.setBackgroundDrawable(null);
				imgView.setImageBitmap(imgBitMap);
			}
		}
		Log.d(TAG,"displaynewimage: " + currDispImage + " " + imageCeiling); //Debugging only
	}

	// This function works only with cached images. It does no fetching from the Internet
	private void displayOldImage(){
		if(currDispImage > 1){
			db.open();
			mCursor = db.getCacheFileName(--currDispImage);
			db.close();

			String cacheFileName = new String(mCursor.getString(mCursor.getColumnIndex("cachefilename")));
			mCursor.close();
			File dispImgFile = new File(bookmarksCacheDir, cacheFileName);
			imgBitMap = BitmapFactory.decodeFile(dispImgFile.toString());
			if(imgBitMap != null){
				imgView.setBackgroundDrawable(null);
				imgView.setImageBitmap(imgBitMap);
			} else { //one possible reason for the bitmap being invalid is that the system
					 //may have chosen to remove files from this application's cache to free
					 //up system resources. In such a case, continuing to click the previous
					 //image button will have the following effect:
					 //1. the same image will keep on being displayed to the user
					 //2. a Toast informing the user that there are no more images in history
					 //    will be displayed
					 //NB: The reason currCachedImage is not reset to 0 (similar to what is done
					 //four lines below) is that there may still be images in the cache (unless
					 // the system deleted ALL the cached files) and the user
					 //may wish to look at them before going on to fetch new images
				Toast.makeText(bookMarkActivity.this, "Cache corrupted", Toast.LENGTH_SHORT).show();
				bookMarkActivity.this.finish();
			}
		}
		else {
			Toast.makeText(bookMarkActivity.this, "This is the first bookmarked image", Toast.LENGTH_SHORT).show();
		}
	}
	
	private boolean saveImage(File saveDir, int Mode){
		switch(Mode){
		case CACHE_MODE:
			if (saveDir != null && saveDir.isDirectory()){
				try{
					String filename = uniqueFileName.format(new Date()).toString()+".jpg";
					File saveImgFile = new File(saveDir, filename);
					FileOutputStream imgSaveStream = new FileOutputStream(saveImgFile);
					BufferedOutputStream bufferedImgSaveStream = new BufferedOutputStream(imgSaveStream);
					imgBitMap.compress(CompressFormat.JPEG,75,bufferedImgSaveStream);
					bufferedImgSaveStream.flush();
					bufferedImgSaveStream.close();	

					//once the image is cached, update the db with the filename of the saved file
					db.open();
					db.insertCacheFileName(imageCeiling, filename);
					db.close();

					//Log.d(TAG, "Image cached to " + saveImgFile.toString());
				} catch(Exception e){
					Log.d(TAG,"Problem caching image");
				}
			}
			return true;
		case SAVE_MODE:
			if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
				try{
					saveDir.mkdirs();
					File saveImgFile = new File(saveDir, uniqueFileName.format(new Date()).toString()+".jpg");
					Log.d(TAG,"Directory: " + saveDir.toString());
					Log.d(TAG,"Filename: " + saveImgFile.toString());
					FileOutputStream imgSaveStream = new FileOutputStream(saveImgFile);
					BufferedOutputStream bufferedImgSaveStream = new BufferedOutputStream(imgSaveStream);
					imgBitMap.compress(CompressFormat.JPEG,75,bufferedImgSaveStream); //the AndroidManifest must give the application the
																																					//requisite permissions for this to work
					bufferedImgSaveStream.flush();
					bufferedImgSaveStream.close();	
					Toast.makeText(this, "Image Saved to " + saveDir.toString(), Toast.LENGTH_SHORT).show();
				} catch(Exception e){
					Log.d(TAG,"Problem saving image");
				}
			} else{
			    Log.d(TAG,"Media is not writable");
			}
			return true;
		default:
			return true;
		}
	}
	
	/**********************************************
	 * * * * * * * * * * * * * * * * * * * * * * *
	 * DIALOG HANDLERS
	 * * * * * * * * * * * * * * * * * * * * * * *
	**********************************************/
	protected Dialog onCreateDialog(int ID){ //called when dialog is created in showDialog function
		switch(ID){
		case BUSY_DRAWING_ID:
			busyDrawingThreadProgressDialog = new ProgressDialog(bookMarkActivity.this);
			busyDrawingThreadProgressDialog.setMessage("Drawing image...");
			busyDrawingThread = new BusyDrawingThread(busyDrawingThreadHandler);
			busyDrawingThread.start();
			return busyDrawingThreadProgressDialog;
		default:
			return null;
		}
	}
	
	/**********************************************
	 * * * * * * * * * * * * * * * * * * * * * * *
	 * THREAD AND THREAD HANDLERS
	 * * * * * * * * * * * * * * * * * * * * * * *
	**********************************************/

	private class BusyDrawingThread extends Thread{
		Handler threadHandler;

		public BusyDrawingThread(Handler h){
			threadHandler = h;
		}
		
		public void run(){
			try{
				db.open();
				mCursor = db.getURL(++imageCeiling);
				db.close();
				if(mCursor != null){
					mCursor.moveToFirst();
					imgURL = new URL(mCursor.getString(mCursor.getColumnIndex("url")));
					currDispImage = imageCeiling;
					//Log.d(TAG, "Drawing Image URL: " + imgURL); // Debugging only
					imgStream = (InputStream) (imgURL.getContent());
					bufferedimgStream = new BufferedInputStream(imgStream);
				}
				threadHandler.sendEmptyMessage(0);
			} catch (MalformedURLException e1) { // invalid URL
				imgStream = null;
				bufferedimgStream = null;
				Log.d(TAG, "URL Malformed"); // Debugging only
			} catch (IOException e) { // HTTP connection problems
				imgStream = null;
				bufferedimgStream = null;
				Log.d(TAG, "HTTP Connection Error"); // Debugging only
			}
		}
	}
	
	final Handler busyDrawingThreadHandler = new Handler() {
		@Override
		public void handleMessage(Message msg){
			dismissDialog(BUSY_DRAWING_ID);	  //the busy drawing dialog can be dismissed
			removeDialog(BUSY_DRAWING_ID);
			if(mCursor != null){
				mCursor.close();
				if(bufferedimgStream != null){

					imgBitMap = BitmapFactory.decodeStream(bufferedimgStream);
					imgView.setBackgroundDrawable(null);
					imgView.setImageBitmap(imgBitMap);

					saveImage(bookmarksCacheDir, CACHE_MODE); //cache the image currently being shown
				}
			} else { //we've walked off the map
				--imageCeiling;
				currDispImage = imageCeiling;
				Toast.makeText(bookMarkActivity.this, "This is the last bookmarked image", Toast.LENGTH_SHORT).show();
			}
			Log.d(TAG,"busydrawinghandler: " + currDispImage + " " + imageCeiling); //Debugging only
		}
	};
	
	/**********************************************
	 * * * * * * * * * * * * * * * * * * * * * * *
	 * GESTURE HANDLERS
	 * * * * * * * * * * * * * * * * * * * * * * *
	**********************************************/
	class flingListenerClass extends SimpleOnGestureListener {
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY){
			// make sure that unintentional taps are discarded
			// (works for both left to right and right to left swipes)
			try{
				if(Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH)
				return false;
			// right to left swipe
			if(((e1.getX() - e2.getX()) >SWIPE_MIN_DISTANCE) && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY){
				if(imageSwitcher.isHapticFeedbackEnabled()){ 
					imageSwitcher.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY,
							android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); //notify the user that a successful swipe has been registered
				}
				imageSwitcher.setInAnimation(slideLeftIn);
				imageSwitcher.setOutAnimation(slideLeftOut);
				displayNewImage();
				Log.d(TAG,"Right to left swipe detected");
			}
			// left to right swipe
			else if(((e2.getX() - e1.getX()) >SWIPE_MIN_DISTANCE) && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY){
				if(imageSwitcher.isHapticFeedbackEnabled()){ 
					imageSwitcher.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY,
							android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); //notify the user that a successful swipe has been registered
				}
				imageSwitcher.setInAnimation(slideRightIn);
				imageSwitcher.setOutAnimation(slideRightOut);
				if(imageCeiling > 0){
					displayOldImage();
				}  else {
					Toast.makeText(bookMarkActivity.this, "No previous images to show", Toast.LENGTH_SHORT).show();
				}
				Log.d(TAG,"Left to right swipe detected");
			}
			return true;
			} catch (Exception e){
				return false;
			}
		}
	}
	
	// onTouchEvent will be called every time a motion event
	// is detected in this activity. We need to pass the event
	// on to the GestureDetector flingDetector's onTouchEvent which
	// will then call flingListenerClass's onFling callback function
	// Since the buttons are not set focusable in touch mode, they will
	// not be affected by swipes
	@Override
	public boolean onTouchEvent(MotionEvent e){
		if(flingDetector.onTouchEvent(e)) return true;
		else return false;
	}
	
	@Override
	public void onDestroy(){
		db.close();
		super.onDestroy();
	}
	
	@Override
	public void onRestart(){
		// --------------------------
		// Check network connectivity
		// --------------------------
		checkNetworkAvailable(bookMarkActivity.this);
		super.onRestart();
	}
}
