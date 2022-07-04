#!/bin/bash

set -e

my_dir="$(dirname "$(realpath "$0")")"
pushd "$my_dir" > /dev/null

res_dir="vector/src/main/res"
god_bubble="$res_dir/drawable/msg_godbubble.xml"

# Multiline sed -i
function mised() {
    local expression="$1"
    local file="$2"
    local tmpfile="$file.tmp"
    cp "$file" "$tmpfile"
    cat "$tmpfile" \
        | tr "\n" "\r" \
        | sed "$expression" \
        | tr "\r" "\n" \
        > "$file"
    rm "$tmpfile"
}

function create_msg_bubble() {
    local is_outgoing="$1"
    local is_rtl="$2"
    local is_notice="$3"
    local has_tail="$4"
    local roundness="$5"

    # Out file name
    local out_bubble="$res_dir/drawable"
    if ((is_rtl)); then
        local out_bubble="$out_bubble-ldrtl"
    fi
    local out_bubble="$out_bubble/msg_bubble"
    if [ ! -z "$roundness" ]; then
        local out_bubble="${out_bubble}_$roundness"
    fi
    if ((is_notice)); then
        local out_bubble="${out_bubble}_notice"
    else
        local out_bubble="${out_bubble}_text"
    fi
    if ((is_outgoing)); then
        local out_bubble="${out_bubble}_outgoing"
    else
        local out_bubble="${out_bubble}_incoming"
    fi
    if !((has_tail)); then
        local out_bubble="${out_bubble}_notail"
    fi
    local out_bubble="${out_bubble}.xml"

    # Copy
    cp "$god_bubble" "$out_bubble"
    echo "$out_bubble"

    # Modify direction
    if [ "$is_rtl" != "$is_outgoing" ]; then
        sed -i 's|left|loft|g;s|Left|Loft|g;s|right|left|g;s|Right|Left|g;s|loft|right|g;s|Loft|Right|g' "$out_bubble"
        mised 's|<!-- LTR tail -->.*<!-- RTL tail -->||' "$out_bubble"
    else
        mised 's|<!-- RTL tail -->.*<!-- tail end -->||' "$out_bubble"
    fi
    # Remove tail
    if ((has_tail)); then
        mised 's|<!-- Filled for no tail -->.*<!-- Filled end -->||' "$out_bubble"
    else
        mised 's|<!-- Filled for tail -->.*\(<!-- Filled for no tail -->\)|\1|' "$out_bubble"
        mised 's|<!-- Tail -->.*<!-- Tail end -->||' "$out_bubble"
        mised 's|<!-- Outer radius -->.*\(<!-- Inner radius -->\)|\1|' "$out_bubble"
        sed -i 's|sc_bubble_radius_in_tail|sc_bubble_radius|g' "$out_bubble"
    fi
    # Modify fill
    if !((is_notice)); then
        sed -i 's|sc_notice_bg|sc_message_bg|g' "$out_bubble"
        mised 's|<!-- Outer radius -->.*\(<!-- Inner radius -->\)|\1|' "$out_bubble"
        mised 's|<!-- Inner radius -->.*<!-- Radius end -->||' "$out_bubble"
    fi
    # Modify color
    if ((is_outgoing)); then
        sed -i 's|_incoming|_outgoing|g' "$out_bubble"
    fi
    # Modify roundness
    if [ ! -z "$roundness" ]; then
        sed -i "s|sc_bubble_radius|sc_bubble_${roundness}_radius|g" "$out_bubble"
    fi
    # Remove unneeded size, which only exists to make it look nicer in drawable preview
    sed -i 's|<size.*/>||g' "$out_bubble"
}

function create_rounded_xml() {
    local roundness="$1"
    local infile="$2"
    if [ ! -z "$roundness" ]; then
        local outfile="$(dirname "$infile")/$(basename "$infile" .xml)_$roundness.xml"
        cp "$infile" "$outfile"
        echo "$outfile"
        sed -i "s|sc_bubble_radius|sc_bubble_${roundness}_radius|g" "$outfile"
    fi
}

for roundness in "" "r1" "r2"; do
    for is_outgoing in 0 1; do
        for is_rtl in 0 1; do
            # Notices are handled via transparency and do not need own drawables right now
            is_notice=0
            #for is_notice in 0 1; do
                for has_tail in 0 1; do
                    create_msg_bubble "$is_outgoing" "$is_rtl" "$is_notice" "$has_tail" "$roundness"
                done
            #done
        done
    done
    create_rounded_xml "$roundness" "$res_dir/drawable/timestamp_overlay.xml"
    create_rounded_xml "$roundness" "$res_dir/drawable-ldrtl/timestamp_overlay.xml"
    create_rounded_xml "$roundness" "$res_dir/drawable/background_image_border_incoming.xml"
    create_rounded_xml "$roundness" "$res_dir/drawable/background_image_border_outgoing.xml"
done


popd > /dev/null
