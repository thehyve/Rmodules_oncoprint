<h2>Heatmap</h2>

<p>
    <div class="plot_hint">
        Click on the heatmap image to open it in a new window as this may increase readability.
        <br><br>
    </div>

    <g:each var="location" in="${imageLocations}">
        <a onclick="window.open('${resource(file: location, dir: "images")}', '_blank')">
            <g:img file="${location}" class="img-result-size"></g:img>
        </a>
    </g:each>

    <div>
        <a href="${resource(file: zipLink)}" class="downloadLink">Download raw R data</a>
    </div>
</p>

