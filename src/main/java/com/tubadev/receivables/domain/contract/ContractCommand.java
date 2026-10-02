package com.tubadev.receivables.domain.contract;

import com.tubadev.receivables.domain.AssertionConcern;

public sealed interface ContractCommand extends AssertionConcern {

    /** The PDF was rendered: keep its name and hash with the contract. */
    record AttachDocument(String filename, String sha256) implements ContractCommand {
        public AttachDocument {
            this.assertArgumentNotEmpty(filename, "'filename' should not be empty");
            this.assertArgumentNotEmpty(sha256, "'sha256' should not be empty");
        }
    }
}
