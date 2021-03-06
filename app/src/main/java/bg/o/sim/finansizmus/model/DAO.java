package bg.o.sim.finansizmus.model;

//TODO - Currently the Db is structured to simulate a server-side Db on the device it is installed,
//TODO - exemplī grātiā - it holds all Users' data, meaning all registrations and accounts are solely local.
//TODO - I should research and implement a central server on my VPS, to which the app will then connect, post and receive data
//TODO - if possible, avoid making it an always-online app, rather, require networking only on registration and occasional server-sync.


//TODO!!! - hash dem passwords boi. The whole plain-text storing, manipulating and transferring of User passwords thing is pure cancer.

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.joda.time.DateTime;

import bg.o.sim.finansizmus.LoginActivity;
import bg.o.sim.finansizmus.MainActivity;
import bg.o.sim.finansizmus.R;
import bg.o.sim.finansizmus.RegisterActivity;
import bg.o.sim.finansizmus.utils.Util;

/**
 * Data Access Singleton Object for the app's's SQLite Db.
 */
public class DAO {

    private static DAO instance;
    private DbHelper h;

    private DAO(@NonNull Context context) {
        if (context == null)
            throw new IllegalArgumentException("Context MUST ne non-null!!!");
        this.h = DbHelper.getInstance(context);
    }

    public static DAO getInstance(Context context) {
        if (instance == null)
            instance = new DAO(context);
        return instance;
    }

    /**
     * Checks the SQLite Db for a {@link User} entry, matching the parameters. If one is found, //TODO - fill in doc
     *
     * @param mail     User e-mail.
     * @param password User password.
     * @return <code>null</code> if logIn unsuccessful. User instance otherwise.
     */
    @Nullable
    public User logInUser(String mail, String password) {
        if (mail == null || password == null || mail.isEmpty() || password.isEmpty())
            return null;

        User user = null;

        String selection = DbHelper.USER_COLUMN_MAIL + " = ? AND " + DbHelper.USER_COLUMN_PASSWORD + " = ? ";
        String[] selectionArgs = {mail, password};

        Cursor cursor = h.getReadableDatabase().query(DbHelper.TABLE_USER, null, selection, selectionArgs, null, null, null);
        if (cursor.moveToNext()) {
            String name = cursor.getString(cursor.getColumnIndex(DbHelper.USER_COLUMN_NAME));
            long id = cursor.getLong(cursor.getColumnIndex(DbHelper.USER_COLUMN_ID));

            if (id < 0) return null;

            user = new User(mail, name, id);
            Cacher.setLoggedUser(user);
        }

        cursor.close();
        return user;
    }

    /**
     * If no previous {@link User} entry with the same e-mail exists, adds a new User to the Db.
     * @param name User's personal name.
     * @param mail User's e-mail. <b>Must be unique.</b>
     * @param password User's password.
     * @return <code>false</code> if e-mail is already taken, <code>true</code> otherwise.
     */
    public boolean registerUser(final String name, final String mail, final String password, final RegisterActivity activity){
        if (name == null || mail == null || password == null || name.isEmpty() || mail.isEmpty() || password.isEmpty())
            return false;

        new AsyncTask<Void, Void, User>(){

            @Override
            protected User doInBackground(Void... params) {
                ContentValues values = new ContentValues(3);
                values.put(DbHelper.USER_COLUMN_MAIL, mail);
                values.put(DbHelper.USER_COLUMN_NAME, name);
                values.put(DbHelper.USER_COLUMN_PASSWORD, password);

                long id = -1;

                try{
                    id = h.getWritableDatabase().insertWithOnConflict(DbHelper.TABLE_USER, null, values, SQLiteDatabase.CONFLICT_ROLLBACK);
                } catch (SQLiteException e){
                    // From what I gather, insertWithOnConflict is 'obsolete' and doesn't work properly... since at-least 2010...
                    // https://issuetracker.google.com/issues/36923483
                    // but our overlords at Google decided that marking that in the method docs or a @Deprecated annotation
                    // would make life too easy I guess.
                    // TODO - reconsider using plain .insert() or check if insertOrThrow() works as documented.
                }

                if (id == -1) return null;

                User u = new User(mail, name, id);

                addDefaultEntries(u);

                return u;
            }

            @Override
            protected void onPostExecute(User user) {
                if (user == null)
                    Util.toastLong(activity, activity.getString(R.string.message_email_taken));
                else{
                    Cacher.setLoggedUser(user);
                    Intent i = new Intent(activity, MainActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    Util.toastLong(activity, activity.getString(R.string.message_hello) + " " + user.getName() + "!");
                    activity.startActivity(i);
                    activity.finish();
                }
            }
        }.execute();
        return true;
    }
    private void addDefaultEntries(User u) {
        long id = u.getId();
        insertCategory("Vehicle", R.mipmap.car, id, Category.Type.EXPENSE);
        insertCategory("Clothes", R.mipmap.clothes,  id, Category.Type.EXPENSE);
        insertCategory("Health", R.mipmap.heart,  id, Category.Type.EXPENSE);
        insertCategory("Travel", R.mipmap.plane,  id, Category.Type.EXPENSE);
        insertCategory("House", R.mipmap.home,  id, Category.Type.EXPENSE);
        insertCategory("Sport", R.mipmap.swimming,  id, Category.Type.EXPENSE);
        insertCategory("Food", R.mipmap.restaurant,  id, Category.Type.EXPENSE);
        insertCategory("Transport", R.mipmap.train,  id, Category.Type.EXPENSE);
        insertCategory("Entertainment", R.mipmap.cocktail,  id, Category.Type.EXPENSE);
        insertCategory("Phone", R.mipmap.phone,  id, Category.Type.EXPENSE);

        insertAccount("Cash", R.mipmap.cash, id);
        insertAccount("Debit", R.mipmap.visa, id);
        insertAccount("Credit", R.mipmap.mastercard, id);

        insertCategory("Salary", R.mipmap.coins,   id, Category.Type.INCOME);
        insertCategory("Savings", R.mipmap.money_box,   id, Category.Type.INCOME);
        insertCategory("Other", R.mipmap.money_bag,   id, Category.Type.INCOME);
    }

    //TODO -  decide on insertMethod return type.
    //TODO -- options include : object created / null, bool 'isSuccessful', db id / -1.

    public void insertAccount(String name, int iconId, long userId) {
        if (name == null || name.isEmpty() || iconId <= 0 || userId < 0) return;

        ContentValues values = new ContentValues(3);
        values.put(DbHelper.ACCOUNT_COLUMN_NAME, name);
        values.put(DbHelper.ACCOUNT_COLUMN_ICON_ID, iconId);
        values.put(DbHelper.ACCOUNT_COLUMN_USER_FK, userId);

        long id = -1;

        try{
            id = h.getWritableDatabase().insertWithOnConflict(DbHelper.TABLE_ACCOUNT, null, values, SQLiteDatabase.CONFLICT_ROLLBACK);
        } catch (SQLiteException e){
            //See @ previous use of insertWithOnConflict, for info, whi this try-catch is here.
        }
        if (id < 0) return;

        Cacher.addAccount( new Account(name, iconId, id));
        Log.i("DAO/LOADER: ", "INSERTED ACC: " + name);
    }
    public void insertCategory(String name, int iconId, long userId, Category.Type type) {
        if (name == null || name.isEmpty() || iconId <= 0 || userId < 0 || type == null) return;

        ContentValues values = new ContentValues(5);
        values.put(DbHelper.CATEGORY_COLUMN_NAME, name);
        values.put(DbHelper.CATEGORY_COLUMN_ICON_ID, iconId);
        values.put(DbHelper.CATEGORY_COLUMN_USER_FK, userId);
        values.put(DbHelper.CATEGORY_COLUMN_IS_EXPENSE, type == Category.Type.EXPENSE ? 1 : 0);

        long id = -1;

        try {
            id = h.getWritableDatabase().insertWithOnConflict(DbHelper.TABLE_CATEGORY, null, values, SQLiteDatabase.CONFLICT_ROLLBACK);
        } catch (SQLiteException e) {
            //See @ previous use of insertWithOnConflict, for info, why this try-catch is here.
        }

        if (id < 0) return;

        Cacher.addCategory( new Category(name, iconId, id, type));
        Log.i("DAO/LOADER: ", "INSERTED CAT: " + name);
    }
    public void insertTransaction(Category cat, Account acc, DateTime date, String note, double sum){
        if (cat == null || acc == null || date == null || note == null || note.length() > 255 || sum <=0) return;

        long userId = Cacher.getLoggedUser().getId();
        long catId = cat.getId();
        long accId = acc.getId();

        ContentValues values = new ContentValues(6);
        values.put(DbHelper.TRANSACTION_COLUMN_USER_FK, userId);
        values.put(DbHelper.TRANSACTION_COLUMN_CATEGORY_FK, catId);
        values.put(DbHelper.TRANSACTION_COLUMN_ACCOUNT_FK, accId);
        values.put(DbHelper.TRANSACTION_COLUMN_DATE, date.getMillis());
        values.put(DbHelper.TRANSACTION_COLUMN_NOTE, note);
        values.put(DbHelper.TRANSACTION_COLUMN_SUM, sum);

        long id = -1;

        try {
            id = h.getWritableDatabase().insertWithOnConflict(DbHelper.TABLE_TRANSACTION, null, values, SQLiteDatabase.CONFLICT_ROLLBACK);
        } catch (SQLiteException e) {
            //See @ previous use of insertWithOnConflict, for info, why this try-catch is here.
        }

        if (id < 0) return;

        Cacher.addTransaction(new Transaction(id, userId, cat, acc, date, note, sum));
        Log.i("DAO/LOADER: ", "INSERTED TRANS: " + sum);
    }


    /**
     * Singleton {@link SQLiteOpenHelper} implementation class.
     */
    static class DbHelper extends SQLiteOpenHelper {

        //DataBase version const:
        private static final int DB_VERSION = 5;

        //DateBase name const
        private static final String DB_NAME = "finansizmus.db";

        //Table name consts
        protected static final @TableName String TABLE_USER = "user";
        protected static final @TableName String TABLE_ACCOUNT = "account";
        protected static final @TableName String TABLE_CATEGORY = "category";
        protected static final @TableName String TABLE_TRANSACTION = "transaction_table"; //3 hours...... 3 FUCKING hours of debugging and repetitive head injuries, because 'transaction' is not a valid SQL table name. fml  (ノಠ益ಠ)ノ彡┻━┻
        protected static final String[] TABLES = {TABLE_USER, TABLE_ACCOUNT, TABLE_CATEGORY, TABLE_TRANSACTION};
        //Common column name consts for easier editing
        private static final String COMMON_COLUMN_ID = "_id";
        private static final String COMMON_COLUMN_USER_FK = "userFK";
        private static final String COMMON_COLUMN_ICON_ID = "iconID";
        private static final String COMMON_COLUMN_NAME = "name";

        //Specific table columns consts
        //USER columns:
        private static final String USER_COLUMN_ID = COMMON_COLUMN_ID;
        private static final String USER_COLUMN_NAME = COMMON_COLUMN_NAME;
        private static final String USER_COLUMN_MAIL = "mail";
        private static final String USER_COLUMN_PASSWORD = "password";
        //ACCOUNT columns:
        protected static final String ACCOUNT_COLUMN_ID = COMMON_COLUMN_ID;
        protected static final String ACCOUNT_COLUMN_USER_FK = COMMON_COLUMN_USER_FK;
        protected static final String ACCOUNT_COLUMN_ICON_ID = COMMON_COLUMN_ICON_ID;
        protected static final String ACCOUNT_COLUMN_NAME = COMMON_COLUMN_NAME;
        protected static final String[] ACCOUNT_QUERY_COLUMNS = { ACCOUNT_COLUMN_ID, ACCOUNT_COLUMN_ICON_ID, ACCOUNT_COLUMN_NAME};
        //CATEGORY columns:
        protected static final String CATEGORY_COLUMN_ID = COMMON_COLUMN_ID;
        protected static final String CATEGORY_COLUMN_USER_FK = COMMON_COLUMN_USER_FK;
        protected static final String CATEGORY_COLUMN_ICON_ID = COMMON_COLUMN_ICON_ID;
        protected static final String CATEGORY_COLUMN_NAME = COMMON_COLUMN_NAME;
        protected static final String CATEGORY_COLUMN_IS_EXPENSE = "isExpense";
        //TRANSACTION columns:
        protected static final String TRANSACTION_COLUMN_ID = COMMON_COLUMN_ID;
        protected static final String TRANSACTION_COLUMN_USER_FK = COMMON_COLUMN_USER_FK;
        protected static final String TRANSACTION_COLUMN_CATEGORY_FK = "categoryFK";
        protected static final String TRANSACTION_COLUMN_ACCOUNT_FK = "accountFK";
        protected static final String TRANSACTION_COLUMN_DATE = "date";
        protected static final String TRANSACTION_COLUMN_NOTE = "note";
        protected static final String TRANSACTION_COLUMN_SUM = "sum";

        //CREATE TABLE statements
        private static final String CREATE_USER = "CREATE TABLE " + TABLE_USER +
                " ( " +
                USER_COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                USER_COLUMN_MAIL + " VARCHAR(80) UNIQUE NOT NULL, " +
                USER_COLUMN_NAME + " VARCHAR(80) NOT NULL, " +
                USER_COLUMN_PASSWORD + " VARCHAR(80) NOT NULL" +
                ");";
        private static final String CREATE_ACCOUNT = "CREATE TABLE " + TABLE_ACCOUNT +
                " ( " +
                ACCOUNT_COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                ACCOUNT_COLUMN_USER_FK + " INTEGER, " +
                ACCOUNT_COLUMN_ICON_ID + " INTEGER, " +
                ACCOUNT_COLUMN_NAME + " VARCHAR(40), " +
                "FOREIGN KEY (" + ACCOUNT_COLUMN_USER_FK + ") REFERENCES " + TABLE_USER + "(" + USER_COLUMN_ID + ")" +
                ");";
        private static final String CREATE_CATEGORY = "CREATE TABLE " + TABLE_CATEGORY +
                " ( " +
                CATEGORY_COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                CATEGORY_COLUMN_USER_FK + " INTEGER, " +
                CATEGORY_COLUMN_ICON_ID + " INTEGER, " +
                CATEGORY_COLUMN_NAME + " VARCHAR(40), " +
                CATEGORY_COLUMN_IS_EXPENSE + " INTEGER, " +
                "FOREIGN KEY (" + CATEGORY_COLUMN_USER_FK + ") REFERENCES " + TABLE_USER + "(" + USER_COLUMN_ID + ")" +
                ");";
        private static final String CREATE_TRANSACTION = "CREATE TABLE " + TABLE_TRANSACTION +
                " ( " +
                TRANSACTION_COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                TRANSACTION_COLUMN_DATE + " INTEGER, " +
                TRANSACTION_COLUMN_NOTE + " VARCHAR(255), " +
                TRANSACTION_COLUMN_SUM + " REAL, " +
                TRANSACTION_COLUMN_USER_FK + " INTEGER, " +
                TRANSACTION_COLUMN_ACCOUNT_FK + " INTEGER, " +
                TRANSACTION_COLUMN_CATEGORY_FK + " INTEGER, " +
                "FOREIGN KEY (" + TRANSACTION_COLUMN_USER_FK + ") REFERENCES " + TABLE_USER + "(" + USER_COLUMN_ID + "), " +
                "FOREIGN KEY (" + TRANSACTION_COLUMN_ACCOUNT_FK + ") REFERENCES " + TABLE_ACCOUNT + "(" + ACCOUNT_COLUMN_ID + "), " +
                "FOREIGN KEY (" + TRANSACTION_COLUMN_CATEGORY_FK + ") REFERENCES " + TABLE_CATEGORY + "(" + CATEGORY_COLUMN_ID + ") " +
                ");";
        //TODO /\ foreign keys to different tables in SQLite, is it doable and if it is - how?

        private static final String[] CREATE_STATEMENTS = {CREATE_USER, CREATE_ACCOUNT, CREATE_CATEGORY, CREATE_TRANSACTION};


        private static DbHelper instance;
        private final Context context;

        private DbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
            this.context = context;
        }

        /**
         * Singleton instance getter.
         *
         * @param context Context instance required for SQLiteOpenHelper constructor.
         * @return Singleton instance of the DbHelper.
         */
        protected static DbHelper getInstance(@NonNull Context context) {
            if (context == null)
                throw new IllegalArgumentException("Context MUST ne non-null!!!");
            if (instance == null)
                instance = new DbHelper(context);
            return instance;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            try {

                for (String s : CREATE_STATEMENTS) {
                    db.execSQL(s+"S");
                    Log.i("CREATED ",s);
                }

                db.execSQL("CREATE INDEX user_mail_index ON " + TABLE_USER + " (" + USER_COLUMN_MAIL + ")");
                db.execSQL("CREATE INDEX acc_user_index ON " + TABLE_ACCOUNT + " (" + ACCOUNT_COLUMN_USER_FK + ")");
                db.execSQL("CREATE INDEX cat_user_index ON " + TABLE_CATEGORY + " (" + CATEGORY_COLUMN_USER_FK + ")");
                db.execSQL("CREATE INDEX trans_user_index ON " + TABLE_TRANSACTION + " (" + TRANSACTION_COLUMN_USER_FK + ")");


            } catch (SQLiteException e) {
                Log.e("ERROR: ", e.toString());
                //In case of unsuccessful table creation, clear any created tables, display appropriate message and restart the app.
                //Fingers crossed, that this Toast never sees the light of day =]
                //TODO - THIS TOAST HAS SPAT EXCEPTIONS BEFORE. TEST THE HELL OUT OF IT.
                // yes, I realize the irony in the error-message throwing an error, but whatyagonnado. The joys of a learning experience I guess =]
                Util.toastLong(context, context.getString(R.string.message_sql_exception));
                for (String s : TABLES)
                    db.execSQL("DROP TABLE IF EXISTS "+ s);

                Intent intent = new Intent(context, LoginActivity.class);
                PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 500, pendingIntent);

                System.exit(0);
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            //TODO keep data from old table.
            for (String s : TABLES)
                db.execSQL("DROP TABLE IF EXISTS " + s + " ;");
            onCreate(db);
        }
    }
}

