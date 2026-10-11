package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushSelectIntoProject(t *testing.T) {
	rule := loadRule(t, "PushSelectIntoProject")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	fcol := e.f.Metadata().AddColumn("f", types.Int)
	fexpr := e.f.ConstructPlus(e.v(cols[0]), e.num(1))
	items := memo.ProjectionsExpr{e.f.ConstructProjectionsItem(fexpr, fcol)}
	project := e.f.ConstructProject(s, items, opt.MakeColSet(cols[0]))
	p := e.f.ConstructGt(e.v(cols[0]), e.num(0))
	q := e.f.ConstructLt(e.v(fcol), e.num(10))
	before := e.f.ConstructSelect(project, e.filters(p, q))
	filtered := e.f.ConstructSelect(s, e.filters(p))
	want := e.f.ConstructSelect(e.f.ConstructProject(filtered, items, opt.MakeColSet(cols[0])), e.filters(q))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(project, e.filters(q, p)))
	e.expectNoMatch(rule, e.f.ConstructSelect(project, e.filters(e.f.ConstructGt(e.v(fcol), e.num(0)), q)))
}

func TestSQLPushSelectIntoProject(t *testing.T) {
	expectSQLRewrites(t, "PushSelectIntoProject", "SELECT * FROM (SELECT x+1 AS f, x FROM (VALUES (1)) AS v(x)) AS q WHERE x>0 AND f<10")
}
