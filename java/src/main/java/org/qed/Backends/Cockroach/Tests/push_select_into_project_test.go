package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushSelectIntoProject(t *testing.T) {
	rule := loadRule(t, "PushSelectIntoProject")
	e := newEnv(t)

	scan, cols := e.scan("t", 1)
	a := cols[0]
	computed := e.f.Metadata().AddColumn("computed", e.num(1).DataType())
	projection := e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(a), e.num(1)), computed)
	project := e.f.ConstructProject(scan, memo.ProjectionsExpr{projection}, opt.MakeColSet(a))
	p := e.f.ConstructEq(e.v(a), e.num(2))
	q := e.f.ConstructEq(e.v(computed), e.num(3))
	before := e.f.ConstructSelect(project, e.filters(p, q))

	filteredScan := e.f.ConstructSelect(scan, e.filters(p))
	wantProject := e.f.ConstructProject(filteredScan, memo.ProjectionsExpr{projection}, opt.MakeColSet(a))
	want := e.f.ConstructSelect(wantProject, e.filters(q))
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructSelect(project, e.filters(
		e.f.ConstructEq(e.v(computed), e.num(2)),
		q,
	))
	e.expectNoMatch(rule, nearMiss)
}
