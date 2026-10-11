package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestEliminateGroupByProject(t *testing.T) {
	rule := loadRule(t, "EliminateGroupByProject")
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	project := e.f.ConstructProject(s, nil, opt.MakeColSet(cols[0]))
	before := e.f.ConstructGroupBy(project, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(cols[0]),
	})
	want := e.f.ConstructGroupBy(s, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(cols[0]),
	})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructGroupBy(s, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(cols[0]),
	}))
	e.expectNoMatch(rule, e.f.ConstructDistinctOn(project, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(cols[0]),
	}))
}

func TestSQLEliminateGroupByProject(t *testing.T) {
	sql := "SELECT x FROM (SELECT x, y FROM u) AS q GROUP BY x"
	before, _ := planFor(t, sql, nil)
	if !strings.Contains(before, "group-by") || !strings.Contains(before, "project") {
		t.Fatalf("expected GroupBy over Project before rewrite:\n%s", before)
	}
	got, fired := planFor(t, sql, nil, "EliminateGroupByProject")
	if strings.Join(fired, ",") != "EliminateGroupByProject" {
		t.Fatalf("expected EliminateGroupByProject to fire, got %v:\n%s", fired, got)
	}
}
