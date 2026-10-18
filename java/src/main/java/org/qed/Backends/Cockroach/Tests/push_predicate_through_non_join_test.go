package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushPredicateThroughNonJoin(t *testing.T) {
	rule := loadRule(t, "PushPredicateThroughNonJoin")
	e := newEnv(t)
	scan, cols := e.scan("t", 1)
	first := e.f.Metadata().AddColumn("first", e.f.Metadata().ColumnMeta(cols[0]).Type)
	second := e.f.Metadata().AddColumn("second", e.f.Metadata().ColumnMeta(cols[0]).Type)
	projections := memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(cols[0]), first),
		e.f.ConstructProjectionsItem(e.v(cols[0]), second),
	}
	projected := e.f.ConstructProject(scan, projections, opt.ColSet{})
	before := e.f.ConstructSelect(projected, e.filters(
		e.f.ConstructEq(e.v(first), e.num(1)),
		e.f.ConstructEq(e.v(second), e.num(2)),
	))

	push := e.f.ConstructSelect(scan, e.filters(e.f.ConstructEq(e.v(cols[0]), e.num(1))))
	wantProject := e.f.ConstructProject(push, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(cols[0]), first),
		e.f.ConstructProjectionsItem(e.v(cols[0]), second),
	}, opt.ColSet{})
	want := e.f.ConstructSelect(wantProject, e.filters(e.f.ConstructEq(e.v(second), e.num(2))))
	e.expectRewrite(rule, before, want)

	nearScan, nearCols := e.scan("t", 1)
	nearFirst := e.f.Metadata().AddColumn("first", e.f.Metadata().ColumnMeta(nearCols[0]).Type)
	nearSecond := e.f.Metadata().AddColumn("second", e.f.Metadata().ColumnMeta(nearCols[0]).Type)
	nearProject := e.f.ConstructProject(nearScan, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(nearCols[0]), nearFirst),
		e.f.ConstructProjectionsItem(e.v(nearCols[0]), nearSecond),
	}, opt.ColSet{})
	near := e.f.ConstructSelect(nearProject, e.filters(
		e.f.ConstructEq(e.v(nearFirst), e.num(1)),
		e.f.ConstructEq(e.v(nearFirst), e.num(2)),
	))
	e.expectRewrite(rule, near, e.f.ConstructProject(
		e.f.ConstructSelect(nearScan, e.filters(e.f.ConstructEq(e.v(nearCols[0]), e.num(1)), e.f.ConstructEq(e.v(nearCols[0]), e.num(2)))),
		memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.v(nearCols[0]), nearFirst),
			e.f.ConstructProjectionsItem(e.v(nearCols[0]), nearSecond),
		}, opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructSelect(nearScan, e.filters(e.f.ConstructEq(e.v(nearCols[0]), e.num(1)))))
}
