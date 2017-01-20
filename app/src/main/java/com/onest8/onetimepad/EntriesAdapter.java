package com.onest8.onetimepad;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.support.design.widget.Snackbar;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class EntriesAdapter extends BaseAdapter {
    private List<Entry> allEntries;
    private List<Entry> visibleEntries;
    private Entry currentSelection;


    @Override
    public int getCount() {
        return visibleEntries != null ? visibleEntries.size() : 0;
    }

    @Override
    public Entry getItem(int i) {
        return getVisibleEntries().get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {

        View v = convertView;

        if (v == null) {
            final LayoutInflater vi;
            vi = LayoutInflater.from(parent.getContext());
            v = vi.inflate(R.layout.row, parent, false);
        }

        if (getVisibleEntries().get(position) == getCurrentSelection()) {
            v.setBackgroundColor(Color.LTGRAY);
        } else if (getItem(position).getShowOTP() && !getIsInActionMode()) {
            v.setBackgroundColor(Color.YELLOW);
        } else {
            v.setBackgroundColor(Color.TRANSPARENT);
        }

        final TextView tt1 = (TextView) v.findViewById(R.id.textViewLabel);
        tt1.setText(getItem(position).getLabel());

        TextView tt2 = (TextView) v.findViewById(R.id.textViewOTP);
        v.setTag(position);
        String otp = getItem(position).getCurrentOTP();
        if (getItem(position).getShowOTP() && !getIsInActionMode()) {
            tt2.setText(otp);
        } else if (otp != null) {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < otp.length(); i++)
                s.append('-');
            tt2.setText(s);
        }

        if (MainActivity.currentEntryIndex == position) {
            tt2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(MainActivity.currentEntryIndex != -1) {
                        Entry entry = getItem(MainActivity.currentEntryIndex);
                        ClipData clip = ClipData.newPlainText(entry.getLabel(), entry.getCurrentOTP());
                        Context context = view.getContext();
                        ClipboardManager clipboard = (ClipboardManager) (context.getSystemService(Context.CLIPBOARD_SERVICE));
                        clipboard.setPrimaryClip(clip);
                        Snackbar.make(convertView, R.string.msg_clipboard_copied, Snackbar.LENGTH_SHORT).show();
                        MainActivity.clipboardExpires = true;
                    } else {
                        Snackbar.make(convertView, R.string.msg_select_entry_first, Snackbar.LENGTH_SHORT).show();
                    }

                }
            });
        }

        v.setOnDragListener(new View.OnDragListener() {

            @Override
            public boolean onDrag(View v, DragEvent event) {

                final int action = event.getAction();
                switch (action) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        if (isFiltered()) {
                            Toast.makeText(
                                    v.getContext(),
                                    R.string.msg_search_nodrag,
                                    Toast.LENGTH_SHORT
                            ).show();
                            return true;
                        }
                        break;

                    case DragEvent.ACTION_DRAG_EXITED:
                        break;

                    case DragEvent.ACTION_DRAG_ENTERED:
                        break;

                    case DragEvent.ACTION_DROP: {
                        int from = Integer.parseInt(event.getClipData().getDescription().getLabel()+"");
                        int to = (Integer) v.getTag();
                        Entry e = getEntries().remove(from);
                        getEntries().add(to, e);
                        getVisibleEntries().clear();
                        getVisibleEntries().addAll(getEntries());
                        notifyDataSetChanged();
                        return true;
                    }

                    case DragEvent.ACTION_DRAG_ENDED: {
                        return true;
                    }

                    default:
                        break;
                }
                return true;
            }
        });
        v.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent arg1) {

                if (isFiltered() && getIsInActionMode()) {
                    Toast.makeText(
                            v.getContext(),
                            R.string.msg_search_nodrag,
                            Toast.LENGTH_SHORT
                    ).show();
                    return true;
                }

                if (getCurrentSelection() != getVisibleEntries().get(position)) {
                    return false;
                }

                ClipData data = ClipData.newPlainText(v.getTag() + "", "");
                View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
                v.startDrag(data, shadow, null, 0);

                return false;
            }
        });

        return v;
    }

    public void setShowOTP(int idx) {
        if (getVisibleEntries() == null)
            return;
        for (int i = 0; i<visibleEntries.size(); i++) {
            Entry e = getVisibleEntries().get(i);
            e.setShowOTP(false);
        }
        if (idx >= 0)
            getVisibleEntries().get(idx).setShowOTP(true);
    }

    public Entry getEntryByLabel(String label) {
        if (getVisibleEntries() == null)
            return null;

        for (int i = 0; i<visibleEntries.size(); i++) {
            Entry e = getVisibleEntries().get(i);
            if (e.getLabel().contains(label))
                return e;
        }
        return null;

    }

    public List<Entry> getEntries() {
        return allEntries;
    }
    List<Entry> getVisibleEntries() {
        return visibleEntries;
    }

    public void setEntries(List<Entry> entries) {
        this.allEntries = entries;
        if (this.visibleEntries == null)
            this.visibleEntries = new ArrayList<Entry>();
        else
            this.visibleEntries.clear();
        this.visibleEntries.addAll(entries);
    }

    public Entry getCurrentSelection() {
        return currentSelection;
    }

    public void setCurrentSelection(Entry currentSelection) {
        this.currentSelection = currentSelection;
    }

    // Filter Class
    public void filter(String charText) {
        charText = charText.toLowerCase(Locale.getDefault());
        visibleEntries.clear();
        if (charText.length() == 0) {
            visibleEntries.addAll(allEntries);
        } else {
            for (Entry e : allEntries) {
                if (e.getLabel().toLowerCase(Locale.getDefault()).contains(charText)) {
                    visibleEntries.add(e);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void resetFilter() {
        visibleEntries.clear();
        visibleEntries.addAll(allEntries);
    }

    public boolean isFiltered() {
        return visibleEntries.size() != allEntries.size();
    }

    private boolean isInActionMode = false;
    public void setIsInActionMode(boolean state) {
        isInActionMode = state;
    }
    public boolean getIsInActionMode() { return isInActionMode; }
}
