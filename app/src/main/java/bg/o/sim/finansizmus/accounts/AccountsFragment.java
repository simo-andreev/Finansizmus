package bg.o.sim.finansizmus.accounts;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import bg.o.sim.finansizmus.MainActivity;
import bg.o.sim.finansizmus.R;
import bg.o.sim.finansizmus.model.Cacher;
import bg.o.sim.finansizmus.favourites.AddCategoryDialogFragment;
import bg.o.sim.finansizmus.favourites.IconsAdapter;

import static bg.o.sim.finansizmus.utils.Const.EXTRA_ICON;

public class AccountsFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_accounts, container, false);

        Context context = getActivity();

        RecyclerView accountsList = view.findViewById(R.id.accounts_list);
        accountsList.setAdapter(new AccountsAdapter(Cacher.getAllAccounts(), context, getFragmentManager()));
        accountsList.setLayoutManager(new LinearLayoutManager(getActivity()));

        RecyclerView moreAccountIconsList = view.findViewById(R.id.accounts_icons_list);
        moreAccountIconsList.setAdapter(new IconsAdapter(Cacher.getAccountIcons(), context));
        moreAccountIconsList.setLayoutManager(new GridLayoutManager(getActivity(), 5));

        moreAccountIconsList.addOnItemTouchListener(
                new RecyclerItemClickListener(context, moreAccountIconsList, (view1, position) -> {

                    AddCategoryDialogFragment dialog = new AddCategoryDialogFragment();
                    Bundle arguments = new Bundle();
                    int iconId = Cacher.getAccountIcons()[position];

                    arguments.putInt(EXTRA_ICON, iconId);
                    arguments.putString("ROW_DISPLAYABLE_TYPE", "ACCOUNT");

                    dialog.setArguments(arguments);
                    dialog.show(getFragmentManager(), String.valueOf(R.string.tag_dialog_add_category));
                })
        );
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        ((MainActivity) getActivity()).setDrawerCheck(R.id.nav_accounts);
    }
}
