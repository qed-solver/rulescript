package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestSimplifyExcept(t *testing.T) {
	rule := loadRule(t, "SimplifyExcept")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("nn", 1)
	out := e.f.Metadata().AddColumn("out", types.Int)
	private := &memo.SetPrivate{LeftCols: opt.ColList{lc[0]}, RightCols: opt.ColList{rc[0]}, OutCols: opt.ColList{out}}
	before := e.f.ConstructExcept(l, r, private)
	anti := e.f.ConstructAntiJoin(l, r, e.filters(e.f.ConstructIs(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	want := e.f.ConstructProject(anti, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(lc[0]), out)}, opt.ColSet{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructExceptAll(l, r, private))
	nonUnique, col := oneColumnUnionAll(e)
	e.expectNoMatch(rule, e.f.ConstructExcept(nonUnique, r, &memo.SetPrivate{
		LeftCols: opt.ColList{col}, RightCols: private.RightCols, OutCols: private.OutCols,
	}))
}

func TestSQLSimplifyExcept(t *testing.T) {
	expectSQLFires(t, "SimplifyExcept", "SELECT x FROM u EXCEPT SELECT a FROM nn")
}
