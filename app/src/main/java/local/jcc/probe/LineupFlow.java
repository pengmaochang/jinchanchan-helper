package local.jcc.probe;

final class LineupFlow {
    enum Action { WAIT, CLOSE_EXISTING, CLOSE_SHOP, OPEN_MENU, IMPORT, REPLACE_THIRD, APPLY_TARGET, FINISH }
    enum Stage { PREPARE, WAIT_BOARD, WAIT_CLOSED, WAIT_MENU, WAIT_IMPORTED, WAIT_APPLY, WAIT_CLOSED_FINAL, DONE }
    static final class View {
        boolean shop,board,menu,replacement,targetMatches,applied,full;
    }
    private Stage stage=Stage.PREPARE;
    private int replacementReads, importedReads, verifiedReads;
    private boolean appliedConfirmed,finalShopClosing,requiresReplacement;
    Stage stage(){return stage;}
    Action next(View view) {
        switch(stage) {
            case PREPARE:
                if(view.menu||view.replacement){stage=Stage.WAIT_BOARD;return Action.CLOSE_EXISTING;}
                if(view.shop){stage=Stage.WAIT_CLOSED;return Action.CLOSE_SHOP;}
                if(view.board){stage=Stage.WAIT_MENU;return Action.OPEN_MENU;}
                return Action.WAIT;
            case WAIT_BOARD:
                if(!view.menu&&!view.replacement){stage=Stage.PREPARE;return next(view);}return Action.WAIT;
            case WAIT_CLOSED:
                if(!view.shop&&view.board){stage=Stage.WAIT_MENU;return Action.OPEN_MENU;}return Action.WAIT;
            case WAIT_MENU:
                if(view.menu){requiresReplacement=view.full;stage=Stage.WAIT_IMPORTED;return Action.IMPORT;}return Action.WAIT;
            case WAIT_IMPORTED:
                if(view.replacement) {
                    importedReads=0;if(++replacementReads<2)return Action.WAIT;
                    stage=Stage.WAIT_APPLY;return Action.REPLACE_THIRD;
                }
                replacementReads=0;
                if(!requiresReplacement&&view.menu&&view.targetMatches) {
                    if(++importedReads<2)return Action.WAIT;
                    stage=Stage.WAIT_APPLY;return next(view);
                }
                importedReads=0;return Action.WAIT;
            case WAIT_APPLY:
                if(!view.menu||!view.targetMatches){verifiedReads=0;return Action.WAIT;}
                if(++verifiedReads<2)return Action.WAIT;
                if(view.applied){appliedConfirmed=true;stage=Stage.WAIT_CLOSED_FINAL;return Action.CLOSE_EXISTING;}
                stage=Stage.WAIT_CLOSED_FINAL;return Action.APPLY_TARGET;
            case WAIT_CLOSED_FINAL:
                if(view.menu&&view.targetMatches&&view.applied){appliedConfirmed=true;return Action.CLOSE_EXISTING;}
                if(appliedConfirmed&&view.shop&&!finalShopClosing){finalShopClosing=true;return Action.CLOSE_SHOP;}
                if(appliedConfirmed&&!view.menu&&!view.replacement&&!view.shop&&view.board){stage=Stage.DONE;return Action.FINISH;}
                return Action.WAIT;
            default:return Action.WAIT;
        }
    }
}
