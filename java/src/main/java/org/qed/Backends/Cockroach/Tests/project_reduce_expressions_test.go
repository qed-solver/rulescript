package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectReduceExpressions(t *testing.T) {
	rule := loadRule(t, "ProjectReduceExpressions")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	z := e.f.Metadata().AddColumn("z", types.Bool)
	project := func(x opt.ScalarExpr) memo.RelExpr {
		return e.f.ConstructProject(s, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(x, z)}, opt.ColSet{})
	}
	positive := e.f.ConstructGt(e.v(cols[0]), e.num(0))
	e.expectRewrite(rule, project(e.f.ConstructAnd(e.f.ConstructFalse(), positive)), project(e.f.ConstructFalse()))
	e.expectNoMatch(rule, project(e.f.ConstructAnd(e.f.ConstructTrue(), positive)))
	e.expectNoMatch(rule, project(positive))
}

func TestSQLProjectReduceExpressions(t *testing.T) {
	expectSQLFires(t, "ProjectReduceExpressions", "SELECT false AND x > 0 AS z FROM u")
}
