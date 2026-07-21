## kfm-ai

Currently, this project has two main functions, 1) import concert set list information from a third-party website and store it locally in a database, and 2) provide a very simple UI/UX to query the database by song name.  It is also written to import set lists for only the Grateful Dead.  I hope to update this project in the future to add the ability to import set lists for *any* band.

[Kiro](https://kiro.dev/) was used in Spec mode to build this project.  The chats and specs generated during the building of this project can be found under the `.kiro` directory.

I want to explain the history of this project to hopefully reduce any possible confusion as to my process.

At first, I expected each concert's set list would need to be "scraped" from an HTML page.  I built a separate library to parse HTML into a document object model.  The parsing worked for a single concert's page.  I was able to import a single concert's set list and store it in the database.

However, when I added the code to iterate through *all* of the concerts for a band, it failed because JavaScript code prevented the systematic iteration.

It was after receiving this error that Kiro informed me there was an API available to do what I was trying to do!  (I was not previously aware of this API.)  

So, I asked Kiro to change the code to use the API instead of the HTML-parsing code.  This worked beautifully!

