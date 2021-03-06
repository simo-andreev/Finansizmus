package bg.o.sim.finansizmus.transactionRelated;

import android.app.DatePickerDialog;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.DatePicker;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import bg.o.sim.finansizmus.MainActivity;
import bg.o.sim.finansizmus.R;
import bg.o.sim.finansizmus.model.Cacher;
import bg.o.sim.finansizmus.model.DAO;
import bg.o.sim.finansizmus.date.DatePickerFragment;
import bg.o.sim.finansizmus.model.Account;
import bg.o.sim.finansizmus.model.Category;
import bg.o.sim.finansizmus.utils.Util;

import static bg.o.sim.finansizmus.utils.Const.EXTRA_NOTE;

public class TransactionFragment extends Fragment implements DatePickerDialog.OnDateSetListener, NoteInputFragment.NoteCommunicator {

    private DAO dao;

    private boolean startedWithCategory;

    //Selection radio between income and expense;
    private RadioGroup catTypeRadio;
    private Spinner accountSelection;
    private ListView categorySelection;

    private Category.Type selectedType;
    private Account selectedAccount;
    private Category selectedCategory;

    private TextView dateDisplay;
    private DateTime date;

    private TextView noteInput;

    private TextView submitButton;

    private boolean isNumpadDown = false;

    private View rootView;

    //=======CALCULATOR==============//
    private static final int MAX_NUM_LENGTH = 9;

    private View numDisplayBase;
    private TextView numDisplay;
    private ImageButton backspace;

    private View numpad;

    private TextView[] numButtons;
    private TextView decimal;

    private TextView equals;
    private TextView divide;
    private TextView multiply;
    private TextView plus;
    private TextView minus;


    //Counts how far past the decimal point the input number is. Used to block input past 2 decimal positions
    private int decimalPosition = BEFORE_DECIMAL;
    private static final int BEFORE_DECIMAL = -1;
    private static final int MAX_DECIMAL_DEPTH = 2;

    /**
     * Flag tracking if there is an operation waiting for execution. Basically: true if there has been numeric input after setting an operation.
     * If <i>true</i>, pressing an operation button will execute the standing arithmetic operation and reset this to false.
     * If <i>false</i>, standing operation can be changed at will until a number is pressed.
     */
    private boolean operationPrimed = false;
    //Tracks what arithmetic operation is currently selected.
    private char currentOperation = OPERATION_NONE;
    private static final char OPERATION_NONE = '=';
    private static final char OPERATION_PLUS = '+';
    private static final char OPERATION_MINUS = '-';
    private static final char OPERATION_MULTIPLY = 'x';
    private static final char OPERATION_DIVIDE = '÷';

    //Previous input. Stored for executing currentOperation.
    private double storedNumber = 0.0;

    //=======CALCULATOR==============//

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_transaction, container, false);

        initializeUiObjects();

        dao = DAO.getInstance(getActivity());

        catTypeRadio.check(R.id.transaction_radio_expense);
        startedWithCategory = checkForCategoryExtra();

        final RowViewAdapter<Category> expenseAdapter = new RowViewAdapter<>(inflater, Cacher.getAllExpenseCategories());
        final RowViewAdapter<Category> incomeAdapter = new RowViewAdapter<>(inflater, Cacher.getAllIncomeCategories());
        categorySelection.setAdapter(expenseAdapter);

        if (selectedType == null)
            selectedType = Category.Type.EXPENSE;

        switch (selectedType) {
            case EXPENSE:
                catTypeRadio.check(R.id.transaction_radio_expense);
                colorizeUI(getActivity(), R.color.colorRed, R.drawable.orange_button);
                categorySelection.setAdapter(expenseAdapter);
                break;
            case INCOME:
                catTypeRadio.check(R.id.transaction_radio_income);
                colorizeUI(getActivity(), R.color.colorGreen, R.drawable.green_button);
                categorySelection.setAdapter(incomeAdapter);
                break;
        }

        accountSelection.setAdapter(new RowViewAdapter<>(inflater, Cacher.getAllAccounts()));

        //Show the current date in a "d MMMM, YYYY" format.
        date = DateTime.now();
        DateTimeFormatter dateFormat = DateTimeFormat.forPattern("d MMMM, YYYY");
        dateDisplay.setText(date.toString(dateFormat));

        //============onClickListeners=============================================================================//

        /* On date click -> pop-up a DateDialogFragment and let user select different date. */
        dateDisplay.setOnClickListener(v -> {
            DatePickerFragment datePicker = DatePickerFragment.newInstance(TransactionFragment.this, date);
            datePicker.show(getFragmentManager(), getString(R.string.tag_dialog_date_picker));
        });

        catTypeRadio.setOnCheckedChangeListener((group, checkedId) -> {
            switch (checkedId) {
                case R.id.transaction_radio_expense:
                    colorizeUI(getActivity(), R.color.colorRedLight, R.drawable.orange_button);
                    categorySelection.setAdapter(expenseAdapter);
                    break;
                case R.id.transaction_radio_income:
                    colorizeUI(getActivity(), R.color.colorGreen, R.drawable.green_button);
                    categorySelection.setAdapter(incomeAdapter);
                    break;
            }
        });

        accountSelection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedAccount = (Account) accountSelection.getItemAtPosition(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        noteInput.setOnClickListener(v -> {
            NoteInputFragment inputFragment = new NoteInputFragment();

            Bundle b = new Bundle();
            b.putString(EXTRA_NOTE, noteInput.getText().toString());
            inputFragment.setArguments(b);

            inputFragment.show(getFragmentManager(), getString(R.string.tag_dialog_note_input));
        });

        categorySelection.setOnItemClickListener((parent, view, position, id) -> {
            selectedCategory = (Category) categorySelection.getItemAtPosition(position);
            createTransaction();
        });

        submitButton.setOnClickListener(v -> {
            if (calculate(OPERATION_NONE) <= 0){
                Util.toastShort(getActivity(), "Transactions must have a greater-than-zero value");
                return;
            }
            if (!startedWithCategory) {
                numpad.animate().setDuration(200).alpha(0.0F).withEndAction(() -> {
                    backspace.setVisibility(View.INVISIBLE);
                    backspace.setClickable(false);
                    numpad.setVisibility(View.GONE);
                    rootView.findViewById(R.id.transaction_section_selection_layout).setAlpha(1F);
                    rootView.findViewById(R.id.transaction_section_selection_layout).setVisibility(View.VISIBLE);
                    isNumpadDown = true;
                });
            } else {
                createTransaction();
            }
        });

        //=========================================================================================================//
        //------------CALCULATOR_LISTENERS------------------------------------------------------------------//
        /*
         * On arithmetic button pressed -> execute stored {@link TransactionActivity#currentOperation}
         *                              -> save new currentOperation
         */
        View.OnClickListener arithmeticListener = v -> {
            if (operationPrimed && currentOperation != OPERATION_NONE)
                calculate(((TextView) v).getText().charAt(0));
            currentOperation = ((TextView) v).getText().charAt(0);
            if (currentOperation != OPERATION_NONE) operationPrimed = false;
        };
        equals.setOnClickListener(arithmeticListener);
        divide.setOnClickListener(arithmeticListener);
        multiply.setOnClickListener(arithmeticListener);
        plus.setOnClickListener(arithmeticListener);
        minus.setOnClickListener(arithmeticListener);

        /* Handles input from the numeric buttons and the decimal-point button.*/
        View.OnClickListener numberListener = v -> {
            //Block input if maximal number of digits after decimal point has been reached
            if (operationPrimed && decimalPosition >= MAX_DECIMAL_DEPTH) return;

            if (v.getId() == R.id.transaction_numpad_decimal) {
                if (!operationPrimed) {
                    operationPrimed = true;
                    storedNumber = Double.valueOf(numDisplay.getText().toString());
                    numDisplay.setText("0.");
                    decimalPosition = BEFORE_DECIMAL + 1;
                } else if (decimalPosition == BEFORE_DECIMAL) {
                    numDisplay.append(".");
                    decimalPosition = BEFORE_DECIMAL + 1;
                }
                return;
            }

            if (operationPrimed && decimalPosition == BEFORE_DECIMAL && numDisplay.getText().toString().length() >= MAX_NUM_LENGTH) return;

            if (!operationPrimed) {
                operationPrimed = true;
                storedNumber = Double.valueOf(numDisplay.getText().toString());
                numDisplay.setText("0");
                decimalPosition = BEFORE_DECIMAL;
            }

            if (numDisplay.getText().toString().equals("0")) numDisplay.setText("");
            numDisplay.append(((TextView) v).getText().toString());
            if (decimalPosition > BEFORE_DECIMAL) decimalPosition++;

        };
        for (TextView btn : numButtons)
            btn.setOnClickListener(numberListener);
        decimal.setOnClickListener(numberListener);

        /* Deletes a single digit (or the dec. point)*/
        backspace.setOnClickListener(v -> {
            //Get currently displayed String representation and its length
            String displayText = numDisplay.getText().toString();
            int length = displayText.length();

            //if the 'display' is a single digit long, replaces it with a 0.
            //else removes last number
            numDisplay.setText(displayText.matches("^-?[0-9]$") ? "0" : displayText.substring(0, --length));
            //If las digit was after the decimal point, move tracker one step back.
            if (decimalPosition != BEFORE_DECIMAL) decimalPosition--;
        });

        //-------END--CALCULATOR_LISTENERS------------------------------------------------------------------//

        return rootView;
    }

    /**
     * Checks if the Transaction input has been started from a specific category and adjusts UI and data accordingly.
     *
     * @return <code>true</code> if the Fragment has been started <b>with</b> an implicit {@link Category}.
     */
    private boolean checkForCategoryExtra() {
//        Bundle args = getArguments();
//        if (args == null) {
//            return false;
//        }
//        Category cat = (Category) args.getSerializable(getString(R.string.EXTRA_CATEGORY));
        if (selectedCategory != null) {
            switch (selectedCategory.getType()) {
                case EXPENSE:
                    catTypeRadio.check(R.id.transaction_radio_expense);
                    ((MainActivity) getActivity()).setDrawerCheck(R.id.nav_expense);
                    break;
                case INCOME:
                    catTypeRadio.check(R.id.transaction_radio_income);
                    ((MainActivity) getActivity()).setDrawerCheck(R.id.nav_income);
                    break;
            }
            selectedType = selectedCategory.getType();
            catTypeRadio.setVisibility(View.GONE);
            submitButton.setText(getString(R.string.capitalized_add_to) + " " + selectedCategory.getName());
            return true;
        }
        return false;
    }

    /**
     * Changes the colours and background of UI elements for the TransactionActivity.
     *
     * @param c Activity Context.
     * @param colourID the @colours value which to use
     * @param ninePatchID a 9patch image's resource id.
     */
    private void colorizeUI(Context c, int colourID, int ninePatchID) {
        numDisplay.setBackgroundResource(colourID);
        backspace.setBackgroundResource(colourID);
        numDisplayBase.setBackgroundResource(colourID);
        dateDisplay.setTextColor(ContextCompat.getColor(c, colourID));

        for (TextView btn : numButtons)
            btn.setBackgroundResource(ninePatchID);

        decimal.setBackgroundResource(ninePatchID);
        equals.setBackgroundResource(ninePatchID);
        plus.setBackgroundResource(ninePatchID);
        minus.setBackgroundResource(ninePatchID);
        multiply.setBackgroundResource(ninePatchID);
        divide.setBackgroundResource(ninePatchID);

        submitButton.setBackgroundResource(ninePatchID);
    }

    /**
     * Creates a Transaction with the note, sum, type and date selected in the Activity and a passed Section.
     */
    private void createTransaction() {

        double sum = calculate(OPERATION_NONE);

        String note = noteInput.getText().toString();

        Account account = (Account) accountSelection.getSelectedItem();
        Category category = selectedCategory;

        dao.insertTransaction(category, account, date, note, sum);

        getActivity().onBackPressed();
    }

    /**
     * Executes stored arithmetic operation.
     */
    private double calculate(char NEXT_OPERATION) {

        //TODO - validate 'NEXT_OPERATION' param.

        double currNumber = Double.valueOf(numDisplay.getText().toString());

        if (currentOperation == OPERATION_NONE) return currNumber;

        double temp = storedNumber;

        switch (currentOperation) {
            case OPERATION_PLUS:
                temp += currNumber;
                break;
            case OPERATION_MINUS:
                temp -= currNumber;
                break;
            case OPERATION_MULTIPLY:
                temp *= currNumber;
                break;
            case OPERATION_DIVIDE:
                temp /= currNumber;
                break;
        }

        if (temp > 999_999_999.99) {
            Util.toastLong(getActivity(), "Sorry, this app doesn't support transactions larger than $999,999,999.99");
            storedNumber = 999_999_999.99;
        } else {
            storedNumber = temp;
        }

        //Round result to 2 decimal places
        storedNumber = Math.round(storedNumber * 100.0) / 100.0;

        String numText;
        String format;

        format = "%.2f";
        numText = String.format(format, storedNumber);
        numText = numText.replaceAll("\\.(00$|0$)", "");

        decimalPosition = numText.contains(".") ? (numText.length() - 1) - numText.lastIndexOf(".") : BEFORE_DECIMAL;

        format = "%." + (decimalPosition == BEFORE_DECIMAL ? 0 : decimalPosition) + "f";
        numText = String.format(format, storedNumber);

        numDisplay.setText(numText);


        currentOperation = NEXT_OPERATION;
        return storedNumber;
    }

    /** Handles selection from the DatePickerFragment. */
    @Override
    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
        date = new DateTime(year, month + 1, dayOfMonth, 0, 0);
        DateTimeFormatter dateFormat = DateTimeFormat.forPattern("d MMMM, YYYY");
        dateDisplay.setText(date.toString(dateFormat));
    }

    /**
     * Moved all the .findViewById([...]) methods here, because the onCreateView was getting a bit cluttered.
     */
    private void initializeUiObjects() {
        catTypeRadio = rootView.findViewById(R.id.transaction_radio);
        dateDisplay = rootView.findViewById(R.id.transaction_date_display);

        noteInput = rootView.findViewById(R.id.transaction_note_input);
        accountSelection = rootView.findViewById(R.id.transaction_account_spinner);

        numDisplayBase = rootView.findViewById(R.id.transaction_number_display);
        numDisplay = rootView.findViewById(R.id.transaction_number_display_text);
        backspace = rootView.findViewById(R.id.transaction_number_display_backspace);

        numpad = rootView.findViewById(R.id.transaction_numpad);

        //Seeing as they act almost identically, put all numeric buttons in an array for easier manipulation.
        numButtons = new TextView[10];
        for (int i = 0; i <= 9; i++)
            numButtons[i] = rootView.findViewById(getResources().getIdentifier("transaction_numpad_" + i, "id", getActivity().getPackageName()));

        decimal = rootView.findViewById(R.id.transaction_numpad_decimal);
        equals = rootView.findViewById(R.id.transaction_numpad_equals);
        divide = rootView.findViewById(R.id.transaction_numpad_divide);
        multiply = rootView.findViewById(R.id.transaction_numpad_multiply);
        plus = rootView.findViewById(R.id.transaction_numpad_plus);
        minus = rootView.findViewById(R.id.transaction_numpad_minus);

        categorySelection = rootView.findViewById(R.id.transaction_account_selection);
        submitButton = rootView.findViewById(R.id.transaction_submit_btn);
    }


    @Override
    public void setNote(String note) {
        if (note != null && !note.isEmpty()) this.noteInput.setText(note);
    }


    /**
     * Factory method for starting the TransactionFragment with a pre-selected Category.Type
     *
     * @param type the desired {@link Category.Type}
     * @return a new {@link TransactionFragment} instance.
     */
    public static TransactionFragment getNewInstance(Category.Type type) {
        if (type == null) {
            type = Category.Type.EXPENSE;
            Log.e("TransactionFragment:", " STARTED WITH A NULL TYPE PARAMETER!!!");
        }
        TransactionFragment t = new TransactionFragment();
        t.selectedType = type;
        return t;
    }

    /**
     * Factory method for starting the TransactionFragment with a pre-selected Category.
     *
     * @param category the desired {@link Category}
     * @return a new {@link TransactionFragment} instance.
     */
    public static TransactionFragment getNewInstance(Category category) {
        if (category == null)
            Log.e("TransactionFragment:", " STARTED WITH A NULL CATEGORY PARAMETER!!!");

        TransactionFragment t = new TransactionFragment();
        t.selectedCategory = category;
        return t;
    }
    @Override
    public void onResume() {
        super.onResume();
        int drawerPosition = selectedType == Category.Type.EXPENSE ? R.id.nav_expense : R.id.nav_income ;
        ((MainActivity) getActivity()).setDrawerCheck(drawerPosition);
    }
}
