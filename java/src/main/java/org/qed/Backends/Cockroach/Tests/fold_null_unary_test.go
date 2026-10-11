package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNullUnary(t *testing.T) {
	rule := loadRule(t, "FoldNullUnary")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	z := e.f.Metadata().AddColumn("z", types.Int)
	nullInt := e.f.ConstructNull(types.Int)
	before := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(&memo.UnaryMinusExpr{Input: nullInt}, z),
	}, opt.ColSet{})
	want := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(nullInt, z),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(cols[0])), z),
	}, opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.num(1)), z),
	}, opt.ColSet{}))
}

func TestSQLFoldNullUnary(t *testing.T) {
	expectSQLFires(t, "FoldNullUnary", "SELECT -CAST(NULL AS INT) AS z FROM u")
}
