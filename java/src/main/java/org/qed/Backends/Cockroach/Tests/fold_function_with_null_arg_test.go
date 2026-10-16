package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldFunctionWithNullArg(t *testing.T) {
	rule := loadRule(t, "FoldFunctionWithNullArg")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	z := e.f.Metadata().AddColumn("z", types.Bool)
	null := e.f.ConstructNull(types.Int)
	before := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructEq(e.v(cols[0]), null), z),
	}, opt.ColSet{})
	want := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructNull(types.Bool), z),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructEq(null, e.v(cols[0])), z),
	}, opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructEq(e.v(cols[0]), e.num(1)), z),
	}, opt.ColSet{}))
}

func TestSQLFoldFunctionWithNullArg(t *testing.T) {
	expectSQLFires(t, "FoldFunctionWithNullArg", "SELECT x = CAST(NULL AS INT) AS z FROM u")
}
